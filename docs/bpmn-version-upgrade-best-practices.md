# BPMN 流程版本升级：最佳实践与主流方案

> 本文聚焦「流程定义升级版本后，如何安全演进 / 处理在途实例」这一主题，梳理业界主流方案与工程纪律。
> 适用 Camunda 7/8、Flowable、Activiti 等编排式 BPMN 引擎；文末附与其他范式（Temporal 类）的对比与对本项目的落地建议。
>
> 结论先行：**主流做法是「默认不动 + 前向兼容演进 + 必要时才迁移」，真正做 Process Instance Migration 是少数场景。**

---

## 目录
- [1. 引擎的版本模型（前提）](#1-引擎的版本模型前提)
- [2. 决策框架：要不要动、怎么动](#2-决策框架要不要动怎么动)
- [3. 主流方案（按使用频率）](#3-主流方案按使用频率)
- [4. 变更分级（实操速查）](#4-变更分级实操速查)
- [5. 迁移方法论（方案 D 的正确姿势）](#5-迁移方法论方案-d-的正确姿势)
- [6. 常见反模式](#6-常见反模式)
- [7. 各引擎 / 范式差异](#7-各引擎--范式差异)
- [8. 治理（让策略可执行）](#8-治理让策略可执行)
- [9. 对本项目的落地建议](#9-对本项目的落地建议)

---

## 1. 引擎的版本模型（前提）

- 流程定义是**不可变 + 带版本**的：同名（相同 process `key`）重新部署 → 引擎生成新版本 v(N+1)，旧版本保留（`ACT_RE_PROCDEF`）。
- 每个**运行中实例**绑定在启动时的 `processDefinitionId`（含版本号，如 `Order:3:...`），默认**始终在旧版本上跑完**。
- 新 `startProcessInstanceByKey` 走**最新版本**。
- 这套机制 Camunda / Flowable / Activiti 完全一致 —— 它本身就是「默认安全」的设计。

> 含义：**升级 BPMN 不会影响在途实例**，除非你主动迁移。这是所有策略的出发点。

---

## 2. 决策框架：要不要动、怎么动

按「在途实例能等多久」×「变更是不是破坏性」决定：

| 情况 | 推荐做法 |
|------|----------|
| 短流程 / 在途量小 | **Drain（让旧实例自然跑完）**，直接发新版 |
| 改动是「加法」（加活动/分支/变量/改表达式） | **前向兼容演进**，无需迁移 |
| 长流程在途量大 + 破坏性改动 | **Process Instance Migration** |
| 大版本不兼容（重写） | **新 key（并行版本）+ 灰度切换**，旧栈跑完下线 |

90% 的日常发版落在前两行，根本用不到迁移。

---

## 3. 主流方案（按使用频率）

### 方案 A：Drain-and-Redeploy（最主流）
- 发布新版 → 旧实例在旧版本上继续跑完 → 新实例走新版。
- 配合 **Quiescence（静默）**：发布前先把流程定义 / 相关 job **挂起（suspend）**，停止新建，等在途实例落到安全点（无在途或都在可中断节点），再切版本。Camunda / Flowable 都支持挂起流程定义与 job。
- 适用：绝大多数中短流程。

### 方案 B：前向兼容演进（第二主流，工程纪律）
只做**安全变更**，从源头避免迁移：
- ✅ 新增活动/分支（加在当前没有实例停留的路径上）
- ✅ 改网关条件、表达式、DMN 决策表
- ✅ 换 delegate 的**实现**（class/bean 内容），activity 引用不变
- ✅ 新增流程变量、改表单/UI
- ❌ 不删除/重命名「在途实例可能停留」的活动
- ❌ 不改 activityId

### 方案 C：新 key 并行版本（破坏性变更的首选替代）
- 不去改 `Order` 这个流程，而是新建 `OrderV2`，把**新建实例**路由到 V2；旧 `Order` 跑完即止。
- 优点：完全绕开迁移风险，天然灰度（按业务键分流）。
- 这是业界处理「破坏性大改」最常用的务实做法，比硬迁移更稳。

### 方案 D：Process Instance Migration（真正需要时才用）
长流程（等数天/周/月）+ 破坏性改动、且不能等其自然结束时才用。
- **Camunda 7**：`runtimeService.createProcessInstanceMigration(migrationPlan)`，plan 里 `mapActivities(srcId, tgtId)`，可 `updateEventTriggers`，先 `validate()`。
- **Flowable**：`runtimeService.createProcessInstanceMigrationBuilder().migrateToProcessDefinition(...).addActivityMapping(...).migrateProcessInstance(...)`，先 `validateMigration`。
- **Camunda 8 (Zeebe)**：也有 migration 命令（8.5+），但约束更严（须显式映射当前活动态）。
- 迁移是「移动执行指针」**不重放历史**，已完成节点不会重跑。

### 方案 E：把易变逻辑外置（降低 BPMN 变更频率）
- 把易变的业务规则放到 **DMN 决策表 / 表达式 / 外部服务 / 外部 worker**，BPMN 骨架保持稳定。
- 改规则只动 DMN/外部服务，不用重新部署 BPMN → 从根上减少版本升级场景。

---

## 4. 变更分级（实操速查）

| 级别 | 例子 | 对在途实例 |
|------|------|-----------|
| **安全** | 加活动（空路径上）、改表达式/网关条件、换 delegate 实现、加变量、改表单 | 无影响，可直接发 |
| **需注意** | 改「实例正停留的节点」（如某 user task 的表单） | 该实例继续用旧表单/旧行为；新实例用新 |
| **破坏性** | 删/改 activityId、删被下游读取的变量、重构在途路径 | 在途实例可能无法继续，需迁移或换 key |

---

## 5. 迁移方法论（方案 D 的正确姿势）

1. **先校验（dry-run）**：`validateMigration` / `migrationPlan.validate()`，确认当前活动态在新定义中可迁移。
2. **定义活动映射**：仅当 activityId 变更或实例停在「被删节点」时才需要 `mapActivities` / `addActivityMapping`。
3. **备份**：DB 快照。
4. **小批量灰度**：先迁几个实例 → 监控死信/异常。
5. **全量分批**：批量迁移，控制并发。
6. **回滚预案**：迁回旧版本，或恢复备份。
7. **清理**：确认旧实例清零后，再删除旧版本 delegate 类 / 旧定义。

> **禁止「部署即自动迁移」** —— 迁移永远是受控运维操作。

---

## 6. 常见反模式

- ❌ 在同一 process key 上做破坏性改动却不迁移 → 在途实例直接坏。
- ❌ 发新版后**立刻删旧 delegate 类** → 旧实例执行失败 → 重试 → 死信。
- ❌ 不 `validate` 直接全量迁移。
- ❌ 迁移停在「不可迁移态」的实例（多实例中间、补偿中、某些事件等待态）。
- ❌ 一个引擎库跑多个独立演进的流程应用。

---

## 7. 各引擎 / 范式差异

| 引擎/范式 | 版本与迁移 |
|-----------|-----------|
| **Camunda 7 / Flowable / Activiti** | 不可变版本；默认 drain；PIM API（见方案 D） |
| **Camunda 8 (Zeebe)** | 同样版本化；migration 有命令但约束更严（须映射当前活动态） |
| **IBM BPM / Pega** | 各自带版本 + 迁移工具（IBM process migration、Pega branching/versioning） |
| **Temporal / Cadence**（事件溯源 + 确定性回放） | 用 `GetVersion` API 做版本分支，**无 BPMN 意义上的迁移** —— 回放自动适配；演进靠版本化 worker |
| **AWS Step Functions** | 版本 / 别名（alias）+ 流量分级；无在途迁移，靠重设计 |

> 趋势：**编排式 BPMN 引擎**靠「不可变版本 + drain + 可选迁移」；**确定性回放式引擎**（Temporal 类）靠「版本化代码 + 回放」，迁移问题被回放机制消解。

---

## 8. 治理（让策略可执行）

- **CI 校验 BPMN**：部署前解析/合法性校验。
- **发布前盘点**：统计在途实例数与所在节点，判断是否需要静默 / 迁移。
- **镜像 staging**：用与生产同构的环境跑迁移演练。
- **死信监控**：`ACT_RU_DEADLETTER_JOB` 告警，第一时间发现升级引发的失败。
- **版本策略文档化**：哪些变更升版本号、哪些用新 key、哪些需迁移，写进发布说明。

---

## 9. 对本项目的落地建议

本项目（`zxf-springboot-flowable`）的 saga 是**短流程**（秒~分钟级）：

- **默认 Drain**：`re-deploy=true` 时每次启动生成新版本，旧实例跑完即止，**不需要迁移**。
- **演进遵循前向兼容**：加任务 / 改表达式 / 换 adapter 实现都安全；不要删 / 改 activityId。
- **破坏性重构 → 用新 key**（如 `app1-v17`）并行，旧 `app1-v16` 跑完下线，比硬迁移稳。
- **若未来出现「长停留 saga 实例 + 必须升级」** → 才上 Flowable `createProcessInstanceMigrationBuilder`，按第 5 节方法论做。
- **升级后保留旧 delegate** 直到旧版本实例清零（本项目 delegate 用 `delegateExpression`，旧版本引用的 bean 不能在过渡期删）。

---

## 参考资料

- **Camunda Best Practices**：https://camunda.com/best-practices/
  - *Versioning Process Definitions*、*Managing Process Instances*、*Process Instance Migration*。
- **Flowable 文档**：https://www.flowable.com/open-source/docs/bpmn/
  - Process Instance Migration：https://www.flowable.com/open-source/docs/bpmn/ch09a-Process-Instance-Migration
- 通用关键词：*process definition versioning*、*BPM process instance migration*、*workflow engine rolling deployment*。

---

> **一句话总结：业界主流 = 「不可变版本默认安全 → 优先 drain + 前向兼容 → 破坏性变更用新 key 并行 → 长流程不可等才做受控迁移 → 旧 delegate 保留到清零 + 死信监控」。迁移是例外，不是常规。**
