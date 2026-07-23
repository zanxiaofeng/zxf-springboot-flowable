# zxf-springboot-flowable

本项目是 `zxf-springboot-camunda` 的 **Flowable 7.1.0** 等价实现：相同的 Spring Boot 4.0.7 / JDK 21 / 多模块结构 / 业务功能，仅把流程引擎由 Camunda BPM 7.24 换成 Flowable 7.1.0。基础包名 `zxf.camunda.* → zxf.flowable.*`，模块名 `-camunda- → -flowable-`。

> 目录中各代码文件、BPMN、配置里的「原 Camunda 版 …」注释均为移植说明（记录从 Camunda 改了什么），属有意保留。

## 模块

| 模块 | 端口 | 数据库 | 说明 |
|------|------|--------|------|
| zxf-springboot-flowable-saga-common | — | MySQL×2 | 共享库：SagaBuilder、双数据源、FlowableService、控制器、saga 定义 |
| zxf-springboot-flowable-saga-app-1 | 8090 | MySQL×2 | 部署 App1Saga(补偿) + App3Saga(重试) |
| zxf-springboot-flowable-saga-app-2 | 8091 | MySQL×2 | 部署 App2Saga(补偿+finally-undo) + App4Saga(重试) |
| zxf-springboot-flowable-saga-app-3 | 8092 | MySQL×2 | 部署全部 4 个 saga |
| zxf-springboot-flowable-saga-app-ext | 8093 | MySQL×2 | 原 Camunda 外部任务 saga（Flowable 改为异步 service task） |
| zxf-springboot-flowable-h2 | 8080 | H2 file | Loan 审批流程（静态 BPMN） |
| zxf-springboot-flowable-arch-app | 8190 | H2 file | Payment / Form / Notification 架构参考流程（静态 BPMN） |
| zxf-springboot-flowable-arch-rest | 8191 | 无 | mock 下游 REST 服务（无流程引擎） |

## 构建 / 运行

```bash
# 构建（需完整 JDK 21；系统的 openjdk-21 是 JRE 无 javac，须用 ms-21）
export JAVA_HOME=/home/davis/.jdks/ms-21.0.10
mvn clean package

# saga 模块依赖两个 MySQL（flowable:3306 + business:3307）
docker compose up -d

# 启动示例
java -jar zxf-springboot-flowable-h2/target/zxf-springboot-flowable-h2-1.0.0-SNAPSHOT.jar
java -jar zxf-springboot-flowable-saga-app-1/target/zxf-springboot-flowable-saga-app-1-1.0.0-SNAPSHOT.jar
```

# Database

Flowable 表前缀与 Camunda/Activiti 同源，仍为 `ACT_*`：
- `ACT_RE_*` Repository：流程定义、部署等静态数据。
- `ACT_RU_*` Runtime：流程实例、任务、变量、作业等运行时数据。
- `ACT_ID_*` Identity：用户、组。
- `ACT_HI_*` History：历史数据（本项目 saga 模块 `history-level: none` 关闭）。
- `ACT_GE_*` General：通用数据。
- Flowable 7 额外：`ACT_EVT_*`(Event Registry)、`ACT_FO_*`(Form) 等引擎表。
- 引擎库 schema 由 Flowable 自管（`flowable.database-schema-update: true`）；业务库（`TBL_ORDER`）由 Flyway 管理（saga 模块）。

# Document
- Flowable OSS 文档：https://www.flowable.com/open-source/docs/bpmn/
- Spring Boot 集成：https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot
- 源码：https://github.com/flowable/flowable-engine

# Core Concept
- 流程引擎是「被动」的 Java 代码，运行在调用方线程里（「借用客户端线程」）。外部触发（start process / complete task / correlate message）会让引擎向前推进，直到在每个活动分支上到达「等待态」（user task、消息/定时捕获事件等），引擎把执行持久化到库后等待下次触发。
- 定时事件等不需要外部触发，由引擎的 **job executor** 异步获取并处理（async job / timer job）。

# Retry and Compensate
- 默认失败 job 重试 3 次；可通过 `flowable:failedJobRetryTimeCycle`（如 `R3/PT5S`、`R1/PT0S`）自定义。
- **重试型 saga（App3/App4）**：任务适配器抛 `RuntimeException`，按重试周期重试。
- **补偿型 saga（App1/App2）**：任务适配器失败时抛 `BpmnError`，由错误事件子流程捕获并触发补偿（见下「补偿实现」）。

# Deployment and Version（与 Camunda 的差异）
- **Flowable 没有 deployment-aware job executor 概念**。Camunda 的 deployment-aware 通过 `registerDeploymentForJobExecutor` 把 job executor 绑定到特定部署（版本）；Flowable 的 job executor 采用**基于锁的 job 分发**（`async-job-lock-time` / `timer-lock-time`），所有共享同一库的实例都会争抢并处理所有 job。
- 因此本项目：保留 saga-app-1/2/3 三实例结构、端口、各自部署的 saga 子集、日志线程标签 `saga.app-name`（仍可观察「哪个实例处理了哪个 saga」）；删除 `deployment-aware` 配置项与 `registerDeploymentForJobExecutor` 调用（`FlowableService.registerDeploymentForJobExecutor` 为 no-op + 日志）。

# 补偿实现（关键差异，已调通）

Camunda 版用「事件子流程内 Error StartEvent（按异常类名捕获 Throwable）→ Compensate ThrowEvent」触发 BPMN 补偿。经历史表/字节码诊断，**Flowable 的 BPMN 补偿事件在本场景不可用**：事件子流程内的 compensate throw 只补偿子流程自身作用域；把任务包进子流程再被 boundary error 中断会丢弃补偿注册；compensation boundary + error boundary 同挂一个异步任务会触发 `Parent activity not found`。

故改为**手动补偿**（结构仍由 `SagaBuilder` 生成，对调用方透明）：
1. 补偿型 saga 任务完成时由适配器写入完成标记变量 `VAR_OF_TASKn`；`SagaBuilder.compensationActivity` 登记 `VAR_OF_TASKn:undoBean` 映射。
2. `SagaBuilder.triggerCompensationOnAnyError` 生成错误事件子流程（捕获任意 BpmnError），其中 service task 以 method-expression 调用 `zxf.flowable.saga.base.CompensationDelegate.compensate(chain, execution)`。
3. `CompensationDelegate` 逆序遍历映射，仅对已完成的任务调用对应 undo 适配器 Bean —— 与 Camunda 版「失败任务的已完成前置任务按逆序 undo」语义一致。

# Job & JavaDelegate（与 Camunda 的差异）
- Camunda 的 `camunda:class` 在 Spring Boot 中会把 FQN 解析为 Spring Bean（支持构造注入）；**Flowable 的 `flowable:class` 仅反射实例化、无 Spring DI**。故本项目 saga 任务统一用 `flowable:delegateExpression="${beanName}"`（`SagaBuilder` 把适配器 FQN 转为 Bean 名解析）。
- Flowable 7 移除了 `RuntimeService.correlateMessage` / `createMessageCorrelation`，改用「executionQuery 按 businessKey + messageEventSubscriptionName 查到订阅执行，再 `messageEventReceived`」。
- Camunda 的 `executeWithVariablesInReturn()` / `VariableMap` 在 Flowable 无对应，改为 start 后 `runtimeService.getVariables(id)` 返回 `Map`。

# 流程入口

## saga 模块（启动 app-1/2/3/ext 后）
- `GET /saga/all?count=N`、`GET /saga/app-1..4?count=N[&start=M]`、`GET /saga/byId?count=N&processDefinitionId=...`
- `GET /info/definitions`、`/info/instances`、`/info/jobs/all|failed|active|retry`、`/info/deployments/registered`
- app-1 额外暴露 Flowable BPMN REST API：`http://localhost:8090/process-api`（替代 Camunda webapp+rest；Flowable OSS 不含 webapp/modeler/admin）

## h2 模块（Loan，端口 8080）
- `GET /loan/request?amount=N`、`GET /loan/approve?taskId=...&amount=N`、`GET /loan/updated?executionId=...&amount=N`
- 详见 `zxf-springboot-flowable-h2/Readme.md`

## arch-app 模块（Payment/Form/Notification，端口 8190）
- `GET /app/payment/...`、`POST /app/form/...`、`POST /app/...`
- 需先启动 arch-rest（端口 8191）；详见 `zxf-springboot-flowable-arch-app/Readme.md`

# 问题（Flowable 视角）

> 原项目这两个问题基于 Camunda 的 deployment-aware 机制；Flowable 开源版**没有该机制**，答案如下。

## 问题一
由异构应用（不同应用的不同版本）组成的 Flowable 集群中，如何实现特定应用版本与特定流程版本的绑定，以便特定版本的流程任务能被调度到支持的应用版本，以避免版本不匹配错误（如找不到任务关联的 JavaDelegate）？

**结论：Flowable OSS 开箱即用做不到这种绑定。**
- Camunda 用 `deployment-aware=true` + `registerDeploymentForJobExecutor` 让每个实例的 job executor 只处理自己注册的部署（版本）；本项目 demo 即靠它让 app-1（仅 app1/app3 适配器）、app-2（仅 app2/app4）、app-3（全有）共享一库却不串。
- Flowable 的 job executor 是**基于锁的获取**（`async-job-lock-time` / `timer-lock-time`）：谁先锁住谁执行，**获取时不关心 job 属于哪个部署/版本**。故本项目 `registerDeploymentForJobExecutor` 改为 no-op、删除 `deployment-aware` 配置。
- 要在 Flowable 达到同样效果（无内置支持，需自实现）：
  1. **物理隔离**：每个应用版本用独立 Flowable 库（最干净，无跨版本共享）。
  2. **全量兼容**：让每个实例都具备所有 delegate（即 demo 的 app-3 模式：部署全部 saga + 全部适配器），保证任何实例都能跑任何 job —— **推荐**。
  3. **自定义 job 获取过滤**：扩展 async executor / job 获取查询按「本实例拥有的部署/tenant」过滤（高级，OSS 不提供开关）。
  4. **按 tenant 隔离**：每版本一个 tenant，配合 tenant 维度获取过滤。
- 对本项目 demo：app-1/app-2 各只部署部分 saga 且共享一库 —— Flowable 下**不能可靠复现** Camunda 的 deployment-aware 效果；实际可行的是 app-3（全量）单实例或分库。

## 问题二
- **Q：如何确定哪个进程能处理哪些 Job？是看 Job 对应的 Class 是否在进程中存在吗？**
  A：Flowable 不在「获取时」检查，而在「执行时」才暴露。job executor 按锁竞争获取 job，**获取前不校验** delegate 的 class/bean 是否存在；执行时才解析 `flowable:delegateExpression` / `flowable:class`，缺失则抛异常 → job 失败、按 `failedJobRetryTimeCycle` 重试、最终进死信表 `ACT_RU_DEADLETTER_JOB`。性质同 Camunda `camunda:class` 的 `ClassNotFoundException`（Flowable 用 delegateExpression 时表现为「bean 找不到」）。
- **Q：一个工作流实例的多个 Java Activity Job 是否可以分散到多个不同 Java 进程执行？**
  A：可以。每个 async job 相互独立、各自被「最先抢到锁的实例」执行。同一流程实例的 task-1 可能跑在 app-1、task-2 跑在 app-3。Camunda `deployment-aware=true` 时会绑定限制；Flowable 无此绑定，**自由分散**。
- **Q：若一个 Java 进程没有包含运行某工作流实例所有 Job 对应的类，会发生什么？**
  A：该进程抢到「缺 delegate 的 job」即执行抛异常 → 失败重试 → 重试耗尽进死信（`ACT_RU_DEADLETTER_JOB`），**流程实例卡住无法推进**；且因获取是随机/锁竞争，job 可能**反复被「缺类的进程」抢到而反复失败**，只有恰好「有类的进程」抢到时才成功 → **抖动**。这正是问题一要避免的场景，Flowable 开箱不阻止它发生。

## 对比

| 维度 | Camunda (deployment-aware=true) | Flowable (OSS) |
|------|----------------------------------|----------------|
| job 获取依据 | 部署(版本)绑定 + 锁 | 纯锁竞争 |
| 获取前校验 delegate 是否存在 | 由部署绑定间接保证 | 不校验 |
| 异构集群共享一库 | 可安全分工 | 会串 job、缺类失败/抖动 |
| 缺类时表现 | 不获取该 job（绑定过滤） | 抢到才失败 → 重试 → 死信 |

## 问题三
一个 BPMN 文件升级版本后，之前的流程实例怎么处理？

**核心：默认不自动迁移 —— 旧实例继续跑在旧版本上，新版本只对新启动的实例生效。**（与 Camunda 基础行为一致，差别主要在迁移 API。）

### 1. 默认行为
- Flowable 按版本管理流程定义。重新部署同名（相同 process `key`）BPMN → 生成新版本 v(N+1)，旧版本仍保留在 `ACT_RE_PROCDEF`。
- 已运行实例绑定在**启动时**的 `processDefinitionId`（含版本号，如 `app1-v16:3:...`），继续按**旧版本**定义推进，不受新版本影响。
- `startProcessInstanceByKey` 默认启动**最新版本**。

### 2. 关键风险：旧版本的 delegate 不能马上删
- 旧实例仍执行旧版本里的 `flowable:delegateExpression`/`flowable:class`；升级时若删/改了旧版本用到的 delegate Bean/类 → 旧实例执行到该节点失败（bean 找不到 → 重试 → 死信 `ACT_RU_DEADLETTER_JOB`）。
- 故升级时旧版本 delegate 要**保留到旧实例全部结束**，或先迁移再清理。

### 3. 如何把旧实例迁到新版本 —— 显式「流程实例迁移」(Process Instance Migration)
```java
runtimeService.createProcessInstanceMigrationBuilder()
    .migrateToProcessDefinition(newProcessDefinitionId)   // 或 (procDefId, tenantId)
    .addActivityMapping(ActivityMapping.mapping("oldActivityId").toNewActivityId("newActivityId")) // 仅 activityId 变更时需要
    .migrateProcessInstance(oldInstanceId);               // 或 .migrateProcessInstances(list)
```
- 按 `activityId` 把旧定义活动映射到新定义；Flowable 会校验当前所在活动在新定义中可迁移（存在或显式映射），不满足抛校验异常。
- 迁移是「在版本间移动执行指针」，**不重放历史**，已完成节点不会重跑。
- 文档：https://www.flowable.com/open-source/docs/bpmn/ch09a-Process-Instance-Migration

### 4. 本项目场景（saga `re-deploy`）
- `re-deploy=true`：每次启动新建部署 → 新版本；上一次启动遗留的 saga 实例继续跑**旧版本**（`/info/definitions` 会看到多版本）。
- `re-deploy=false`：若库中已有同名流程定义且资源字节未变，Flowable 按 resource 哈希判断**不重复部署**，沿用现有版本。
- 想让旧实例跟新版本：用上面的 migration API，或等旧实例自然结束后只跑新版本。

### 5. 对比

| 维度 | Camunda | Flowable |
|------|---------|----------|
| 旧实例默认行为 | 跑在旧版本 | 跑在旧版本（相同） |
| 新启动 | 最新版本 | 最新版本（相同） |
| 迁移 API | `ProcessMigrationPlan` / `Migration` | `createProcessInstanceMigrationBuilder` |
| 迁移是否重放历史 | 否 | 否 |

### 6. 建议
- 升级 BPMN 时保留旧 delegate 直到旧实例清零；
- 需热切换用 migration API（兼容性变更无需 mapping；删/改 activityId 需 `addActivityMapping`）；
- 大版本不兼容时，更稳妥是**新版本独立部署 + 灰度切换**，旧实例跑完即止。

# 日志
1. Controller 一定要记录入口日志。
2. 线程起的任务也要有入口日志。
3. Event 等入口也要记录日志。
4. 系统启动后全局只执行一次的 PostConstruct 任务要记录日志。
5. 调下游要记录完成日志（request, response）。
6. 重要分支一定要有日志并尽量附带上下文。
7. 日志一定要记录 caseId 及相关上下文。
8. 只使用 SLF4J（`@Slf4j`）记录日志。
