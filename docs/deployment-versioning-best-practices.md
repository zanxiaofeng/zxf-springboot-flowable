# Flowable 集群部署、Job 调度与流程版本治理（问题汇总与最佳实践）

> 本文面向 `zxf-springboot-flowable`（由 `zxf-springboot-camunda` 移植到 Flowable 7.1.0）的运维场景，汇总三个经典问题的 **Flowable 视角答案** 与 **业界最佳实践**。
> 关键背景：**Flowable 开源版没有 Camunda 的 deployment-aware job executor**，job 调度是纯「锁竞争」，不做按部署/版本的绑定与预检。

---

## 目录
- [总原则](#总原则)
- [问题一：异构集群如何绑定「应用版本 ↔ 流程版本」](#问题一)
- [问题二：如何确定哪个进程能处理哪些 Job](#问题二)
- [问题三：BPMN 升级版本后，旧实例怎么处理](#问题三)
- [业界最佳实践](#业界最佳实践)
- [通用生产加固](#通用生产加固)
- [参考资料](#参考资料)

---

## 总原则

**生产环境几乎不跑「异构版本共享一库」的集群。** 本项目 demo（saga-app-1/2/3 各带部分适配器、共享同一 Flowable 库）是刻意构造的教学反例。业界主流做法是以下之一：

1. **同构集群**：所有节点代码完全一致（含全部 delegate），版本更迭用滚动发布过渡；
2. **库级隔离**：每个流程应用 / 租户 / 版本用独立引擎库；
3. （仅 Camunda）deployment-aware 做版本绑定 —— 但 Camunda 官方也把它定位为「特殊场景」，主推仍是同构。

> 本项目已把 `registerDeploymentForJobExecutor` 改为 no-op、删除 `deployment-aware` 配置项；saga-app-1/2/3 三实例结构仅作演示（端口/部署 saga 子集/线程标签 `saga.app-name` 仍保留）。

---

## 问题一

**由异构应用（不同应用的不同版本）组成的 Flowable 集群中，如何实现「特定应用版本 ↔ 特定流程版本」绑定，以避免版本不匹配错误（如找不到任务关联的 JavaDelegate）？**

### 结论：Flowable OSS 开箱即用做不到这种绑定。
- Camunda 用 `deployment-aware=true` + `registerDeploymentForJobExecutor(deploymentId)`，让每个实例的 job executor 只处理「自己注册的部署(版本)」的 job。
- Flowable 的 job executor 是**基于锁的获取**（`async-job-lock-time` / `timer-lock-time`）：谁先锁住谁执行，**获取时不关心 job 属于哪个部署/版本**。
- 后果：异构应用共享同一 Flowable 库时，部署了「仅 app-v2 才有的 delegate」的流程 v2，若 app-v1 也在跑 job executor，app-v1 可能抢到 v2 的 job 并执行失败（delegateExpression bean 找不到）。

### Flowable 下达到同等效果的方案（无内置支持，需自实现）
1. **物理隔离**：每个应用版本用独立 Flowable 库（最干净，无跨版本共享）。
2. **全量兼容**：让每个实例都具备所有 delegate（即 demo 的 app-3 模式：部署全部 saga + 全部适配器），保证任何实例都能跑任何 job —— **推荐**。
3. **自定义 job 获取过滤**：扩展 async executor / job 获取查询，按「本实例拥有的部署/tenant」过滤（高级，OSS 不提供开关，需写代码）。
4. **按 tenant 隔离**：每个应用版本一个 tenant，配合 tenant 维度的获取过滤。

> 对本项目 demo：app-1/app-2 各只部署部分 saga 且共享一库 —— Flowable 下**不能可靠复现** Camunda 的 deployment-aware 效果；实际可行的是 app-3（全量）单实例或分库。

---

## 问题二

### Q2.1：如何确定哪个进程能处理哪些 Job？是看 Job 对应的 Class 是否在进程中存在吗？
**Flowable 不在「获取时」检查，而在「执行时」才暴露。**
- job executor 按锁竞争获取 job，**获取前不校验** delegate 的 class/bean 是否存在；
- 执行时才解析 `flowable:delegateExpression="${bean}"` / `flowable:class`；缺失则抛异常 → job 失败、按 `failedJobRetryTimeCycle` 重试、最终进死信表 `ACT_RU_DEADLETTER_JOB`。
- 性质同 Camunda `camunda:class` 的 `ClassNotFoundException`（Flowable 用 delegateExpression 时表现为「bean 找不到」）。

### Q2.2：一个工作流实例的多个 Java Activity Job 是否可以分散到多个不同 Java 进程执行？
**可以。** 每个 async job 相互独立、各自被「最先抢到锁的实例」执行。同一流程实例的 task-1 可能跑在 app-1、task-2 跑在 app-3。Camunda `deployment-aware=true` 时会绑定限制；Flowable 无此绑定，**自由分散**。

### Q2.3：若一个 Java 进程没有包含运行某工作流实例所有 Job 对应的类，会发生什么？
- 该进程抢到「缺 delegate 的 job」即执行抛异常 → 失败重试 → 重试耗尽进死信（`ACT_RU_DEADLETTER_JOB`），**流程实例卡住无法推进**；
- 且因获取是随机/锁竞争，job 可能**反复被「缺类的进程」抢到而反复失败**，只有恰好「有类的进程」抢到时才成功 → **抖动**。
- 这正是问题一要避免的场景，Flowable 开箱不阻止它发生。

---

## 问题三

**一个 BPMN 文件升级版本后，之前的流程实例怎么处理？**

### 核心：默认不自动迁移 —— 旧实例继续跑在旧版本上，新版本只对新启动的实例生效。
（与 Camunda 基础行为一致，差别主要在迁移 API。）

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

---

## 对比速查

### Camunda (deployment-aware=true) vs Flowable (OSS)

| 维度 | Camunda (deployment-aware=true) | Flowable (OSS) |
|------|----------------------------------|----------------|
| job 获取依据 | 部署(版本)绑定 + 锁 | 纯锁竞争 |
| 获取前校验 delegate 是否存在 | 由部署绑定间接保证 | 不校验 |
| 异构集群共享一库 | 可安全分工 | 会串 job、缺类失败/抖动 |
| 缺类时表现 | 不获取该 job（绑定过滤） | 抢到才失败 → 重试 → 死信 |

### BPMN 版本升级（Camunda vs Flowable）

| 维度 | Camunda | Flowable |
|------|---------|----------|
| 旧实例默认行为 | 跑在旧版本 | 跑在旧版本（相同） |
| 新启动 | 最新版本 | 最新版本（相同） |
| 迁移 API | `ProcessMigrationPlan` / `Migration` | `createProcessInstanceMigrationBuilder` |
| 迁移是否重放历史 | 否 | 否 |

---

## 业界最佳实践

### 集群版本绑定 & job 跨进程调度（对应问题一/二）
1. **同构集群 + 滚动升级 + 新旧共存（expand-and-contract）**：发新版时新包**同时保留旧 delegate 类**（不立刻删），逐节点滚动替换；等旧实例都跑完，再在下一个版本删旧类。过渡期任何节点都能跑任何 job，从根上消除「错节点抢到 job」。
2. **delegate 幂等 + 快失败**：job 会重试（同一 delegate 可能被执行多次），service task 必须幂等；万一被「错版本」执行，要快速、干净地失败（不污染业务状态），靠重试/死信兜底。
3. **死信监控告警**：监控 `ACT_RU_DEADLETTER_JOB`，失败 job 及时人工介入 —— 任何方案都该有的兜底。
4. **长耗时/人工任务用外部 worker 解耦**：把易变、长耗时的逻辑放到引擎外的 worker（Camunda External Task / Flowable Event Registry 或自建轮询），引擎内的流程定义保持稳定，降低「改流程就要改引擎内 delegate」的频率。（本项目 saga-app-ext 即 Camunda External Task 思路；Flowable OSS 无原生 external task，需自建或用 Event Registry。）
5. **库级隔离**（多租户/SaaS 常用）：每租户或每版本一套 Flowable schema，彻底避免跨版本串 job。
6. **一个引擎库对应一个流程应用**：一组始终一起部署的流程放在同一库；不要在一个库跑多个独立演进的流程应用。

### BPMN 升级与在途实例（对应问题三）
1. **能不改就不改；改也只做「前向兼容」变更**：新增活动/分支/变量是安全的；**不要删除或重命名「在途实例可能正停留」的活动**。这是处理在途实例最省事、最安全的做法。
2. **默认让旧实例在旧版本上自然跑完（drain）**：版本切换后旧实例继续跑旧版本、跑完即止；新实例走新版本。对短流程/低在途量通常足够。
3. **必须迁移时才用 Process Instance Migration，且「先校验、后分批」**：先 `validateMigration` 干跑 → 明确 `activityMapping` → 备份 + 小批量灰度 → 监控 → 全量；**永远不要「部署即自动迁移」**，迁移是受控运维操作。
4. **版本策略显式化**：不兼容变更才升 process 版本号；纯样式调整可不动版本；用发布说明标注对在途实例的影响。
5. **大版本不兼容 → 灰度/双栈**：新版本独立部署，流量按业务键灰度切到新流程，旧栈跑完下线。比硬迁移更稳。
6. **保留旧 delegate 类直到旧实例清零**（与问题二呼应）。

> 一句话共识：**「同构集群 + 前向兼容变更 + 让旧实例自然跑完 + 必要时受控迁移 + 死信监控」**。本项目 demo 的「异构共享库 + deployment-aware」是教学用的特例，不是生产首选。

---

## 通用生产加固

- **历史留痕**：生产用 `history-level: audit/full`（本项目 saga 为演示压测用 `none`）；便于追溯与迁移校验。
- **job executor 调优**：锁时长（`async-job-lock-time` / `timer-lock-time`）、获取批量（`max-async-jobs-due-per-acquisition`）、线程池按负载调整；监控 job 积压。
- **发布前置检查**：部署前用引擎的校验/解析能力先验证 BPMN 合法性；CI 里加流程定义校验。
- **可观测性**：失败 job、死信、迁移结果、长停留实例都要有指标和告警。
- **幂等性**：所有 service task delegate 设计为幂等（应对 job 重试）。

---

## 参考资料

- **Camunda Best Practices**（业界最系统的流程引擎实践集，多数原则 Flowable 同样适用）：https://camunda.com/best-practices/
  - 特别相关：*Versioning Process Definitions*、*Dealing with Problems and Exceptions*、*Managing Process Instances*。
- **Flowable 文档**：https://www.flowable.com/open-source/docs/bpmn/
  - Process Instance Migration：https://www.flowable.com/open-source/docs/bpmn/ch09a-Process-Instance-Migration
  - Spring Boot 集成：https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot
- 通用关键词：*process definition versioning*、*rolling deployment workflow engine*、*BPM process instance migration*。
