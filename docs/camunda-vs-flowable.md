# Camunda vs Flowable 组件对比

> 基于本项目（`zxf-springboot-flowable`，由 Camunda BPM 7.24 移植到 Flowable 7.1.0）的实践与调研，按组件维度系统对比 **Camunda** 与 **Flowable**，标注开源/社区 vs 商业、版本差异。
> 数据截至 2026-07（Flowable 8.0.0 已于 2026-02 发布；Camunda 8 持续演进）。

---

## 目录
- [总览表（开源/社区版能力）](#总览表)
- [分组件详解](#分组件详解)
  - [1. Modeler（流程建模器）](#1-modeler)
  - [2. WebUI / Tasklist（人工任务）](#2-webui--tasklist)
  - [3. 流程监控 / 运维（Cockpit 类）](#3-流程监控--运维)
  - [4. 身份管理（IDM）](#4-身份管理idm)
  - [5. 运行架构 / 集群](#5-运行架构--集群)
  - [6. 外部任务 / Worker](#6-外部任务--worker)
  - [7. CMMN / DMN / 表单 / 事件](#7-cmmn--dmn--表单--事件)
  - [8. REST API / Actuator](#8-rest-api--actuator)
  - [9. 流程实例迁移](#9-流程实例迁移)
  - [10. Java / Spring Boot / 技术栈](#10-java--spring-boot--技术栈)
- [一句话定位与选型口诀](#一句话定位与选型口诀)

---

## 总览表

| 组件 | Camunda 7 社区 | Camunda 8 社区 | Flowable 6 OSS | Flowable 7/8 OSS |
|------|----------------|----------------|----------------|-------------------|
| **Modeler（建模器）** | Camunda Modeler（桌面，开源，基于 bpmn.io） | Web Modeler（商业）；桌面 Modeler 可用 | flowable-ui Modeler（浏览器，开源） | ❌ 无（用 bpmn.io 或商业 Design） |
| **WebUI（Tasklist）** | ✅ Tasklist（免费） | ✅ Tasklist（免费） | ✅ Task app（免费） | ❌ 无 |
| **流程监控/运维** | ✅ Cockpit 社区版（基础免费） | ❌ Operate（商业） | ✅ Admin app（基础，免费） | ❌ 无（REST + Actuator 替代） |
| **身份管理（IDM）** | ✅ Admin app（免费）+ LDAP | Identity（商业/托管） | ✅ IDM app（免费） | ❌ 无 UI（IDM 引擎在） |
| **REST API** | ✅ `/engine-rest` | ✅ API + Zeebe 客户端 | ✅ `/flowable-rest`、`/process-api` | ✅ 同左 |
| **Actuator 指标** | — | — | ✅ `/actuator/flowable` | ✅ 同左 |
| **DMN（决策表）** | ✅ DMN 引擎（免费） | ✅ 决策引擎（免费） | ✅ DMN 引擎（免费） | ✅ 同左 |
| **CMMN（案例管理）** | 有限 | ❌ 无重点 | ✅ 完整 CMMN（强项） | ✅ 完整 CMMN |
| **表单（Forms）** | 嵌入/外部表单（Camunda Forms 商业） | ✅ Camunda Forms（form-js，开源） | Form 引擎（6.x 有 UI） | Form 引擎（无 UI） |
| **外部任务/Worker** | ✅ External Task（免费） | ✅ Job Worker（核心范式，免费） | ❌ 无原生外部任务 | ❌ 无（用 Event Registry / async task） |
| **事件处理** | message/signal（BPMN） | message/signal 命令 | ✅ Event Registry（统一事件模型）+ message/signal | ✅ 同左 |
| **流程实例迁移（PIM）** | ✅ Process Migration API | ✅ Migration 命令（8.5+） | ✅ ProcessInstanceMigrationBuilder | ✅ 同左 |
| **集群模型** | 共享 DB + job executor（deployment-aware 可选） | 分布式 broker（Raft 分区，无共享 DB） | 共享 DB + 锁竞争 job executor（无 deployment-aware） | 同左 |
| **运行模式** | 嵌入式 / 共享引擎 | 客户端-服务器（broker，不可嵌入） | 嵌入式 / 独立 | 同左 |
| **Java / Spring Boot** | Java 17，SB 3（社区补丁上 SB4） | Java 17+，SB 3 | Java 11+，SB 2/3 | **Java 17+，SB 4，Spring 7，Jackson 3** |

---

## 分组件详解

### 1. Modeler

| | Camunda | Flowable |
|--|---------|----------|
| **6.x / 7 社区** | **Camunda Modeler**（桌面应用，开源 Apache 2，底层 bpmn.io），支持 BPMN/DMN/CMMN | **flowable-ui Modeler**（浏览器端，开源 AngularJS），集成在 flowable-ui app，可在线画图+部署 |
| **8 / 商业** | Camunda 8 **Web Modeler**（浏览器协同建模，商业）；桌面 Modeler 仍可画 Zeebe 流程 | 商业 **Flowable Design**（现代建模器） |
| **7/8 OSS 无 UI 时** | 用 Camunda Modeler 桌面或 bpmn.io | 用 **bpmn.io / bpmn-js** 独立画图，`.bpmn` 放进项目目录自动部署 |

> 本项目（Flowable 7.1 OSS）无建模器；流程定义（`.bpmn`）直接放在 `bpmn/` 目录由 Flowable 自动部署，或由 `SagaBuilder` 运行时生成。

### 2. WebUI / Tasklist

| | Camunda | Flowable |
|--|---------|----------|
| **7 社区 / 6 OSS** | `camunda-bpm-spring-boot-starter-webapp` → **Tasklist + Cockpit + Admin**（社区免费）。**这是本项目移植后丢失的 UI。** | flowable-ui **Task app**（任务收件箱，免费） |
| **8 / 7·8 OSS** | **Tasklist** 开源免费；Operate/Console 商业 | ❌ **无任务 UI**；任务操作走 REST（`/process-api/task`）或自建前端 |
| **商业** | Camunda 8 Tasklist（免费）/ Operate（商业） | Flowable Work 任务收件箱（商业） |

### 3. 流程监控 / 运维

| | Camunda | Flowable |
|--|---------|----------|
| **7 社区 / 6 OSS** | **Cockpit** 社区版：流程实例、失败 job、部署定义、流程图高亮当前节点（基础免费）；企业版加分析/热力图/告警 | **Admin app**：引擎/流程/任务基础查询与诊断（免费） |
| **8 / 7·8 OSS** | **Operate**（实例/incident 监控）—— 商业 | ❌ **无运维 UI**。替代：`/actuator/flowable`（聚合指标）+ `/process-api` REST（CRUD）+ 自建 `/info/*` 端点 |
| **商业** | Optimize（分析，商业） | Flowable **Control / Insight**（运维与分析，商业） |

> 本项目用 `/actuator/flowable` + `/process-api`（saga-app-1）+ 各模块 `/info/*` 端点做轻量监控。

### 4. 身份管理（IDM）

| | Camunda | Flowable |
|--|---------|----------|
| **7 社区 / 6 OSS** | **Admin** app 管用户/组/授权（免费）；可接 LDAP / Spring Security | **IDM app**（免费） |
| **7/8 OSS** | Camunda 8 Identity（商业/托管） | ❌ 无 UI；IDM **引擎**在（用户/组/权限 API），靠 REST 或集成自有身份 |

### 5. 运行架构 / 集群

| | Camunda 7 | Camunda 8 (Zeebe) | Flowable 6/7/8 |
|--|-----------|--------------------|-----------------|
| **模型** | 传统 **DB 共享引擎**，job executor 轮询 DB | **云原生分布式 broker**（Raft 分区、事件流），客户端经网络连接 | 传统 **DB 共享引擎**（与 Camunda 7 同类），async executor 基于锁 |
| **deployment-aware** | ✅ 可把 job 绑定到特定部署版本（本项目原 demo 用法） | N/A（broker 模式） | ❌ **无**（纯锁竞争） |
| **吞吐** | 中（DB 轮询瓶颈） | **高**（流式、无 DB 轮询） | 中（DB 轮询） |
| **嵌入** | ✅ 嵌入式 / 共享引擎 | ❌ 不可嵌入（客户端-broker） | ✅ 嵌入式 / 独立 |

### 6. 外部任务 / Worker

| | Camunda | Flowable |
|--|---------|----------|
| **机制** | **External Task**（7）/ **Job Worker**（8）—— 把长耗时、易变逻辑放到引擎外的核心范式，**开源免费** | **无原生外部任务**概念；等价做法：async service task + job executor，或用 **Event Registry** 事件驱动解耦 |
| **本项目** | 原 saga-app-ext 用 Camunda External Task + LocalExternalTaskWorker | 改为异步 service task + Flowable job executor（移除 worker） |

### 7. CMMN / DMN / 表单 / 事件

| 组件 | Camunda | Flowable |
|------|---------|----------|
| **CMMN** | Camunda 7 有限；Camunda 8 不重点 | ✅ **完整 CMMN 引擎（Flowable 强项）** |
| **DMN** | ✅ 免费（7 DMN 引擎 / 8 决策引擎） | ✅ 免费（全版本） |
| **表单** | 嵌入/外部表单；Camunda 8 **Camunda Forms (form-js)** 开源 | Form 引擎（6.x 有 UI；7/8 无 UI） |
| **事件** | BPMN message/signal + Zeebe 命令 | ✅ **Event Registry**（统一事件模型，含 Kafka/RabbitMQ/JMS 适配）+ message/signal |

> Flowable 7/8 的 Event Registry 是较新的亮点：把外部事件（Kafka/MQ）与 BPMN/CMMN 事件统一，Camunda OSS 无直接等价。

### 8. REST API / Actuator

| | Camunda | Flowable |
|--|---------|----------|
| **REST** | ✅ `/engine-rest`（7）/ API + Zeebe 客户端（8） | ✅ `/flowable-rest` 或 Spring Boot `/process-api` |
| **Actuator** | — | ✅ `/actuator/flowable`（流程定义数、运行实例数、完成任务数等聚合指标） |

### 9. 流程实例迁移

| | Camunda | Flowable |
|--|---------|----------|
| **API** | `ProcessMigrationPlan` / `Migration`（7）；Migration 命令（8，8.5+） | `runtimeService.createProcessInstanceMigrationBuilder()` |
| **行为** | 移动执行指针、不重放历史、需 activityId 映射 | 同左 |

> 详见 `docs/bpmn-version-upgrade-best-practices.md`。

### 10. Java / Spring Boot / 技术栈

| | Camunda 7 社区 | Camunda 8 | Flowable 6 | Flowable 7 | Flowable 8 |
|--|----------------|-----------|------------|------------|------------|
| **Java** | 17 | 17+ | 11+ | 17+ | 17+ |
| **Spring Boot** | 3（社区补丁上 4） | 3 | 2 / 3 | 3 / 4 | **4** |
| **Spring** | 6 | 6 | 5 / 6 | 6 / 7 | **7** |
| **Jackson** | 2 | 2 | 2 | 2 / 3 | **3** |
| **发布** | 维护中（官方主推转向 8） | 活跃 | 维护 | 活跃 | **最新（2026-02）** |

> 本项目用 Flowable 7.1.0（SB 4 / Spring 7 / Java 21）。Flowable 8.0.0 技术栈与之相同（SB 4 / Spring 7 / Jackson 3），升级到 8 主要是版本号切换。

---

## 一句话定位与选型口诀

| 版本 | 定位 |
|------|------|
| **Camunda 7 社区** | 开箱带 Web UI（Tasklist/Cockpit/Admin），生态成熟，**最适合「想要免费 UI + 传统 DB 引擎」**；官方主推已转向 8。 |
| **Camunda 8 社区** | 云原生、高吞吐、External Worker 范式；Tasklist 免费，但**监控/建模/运维 UI 多为商业**。 |
| **Flowable 6 OSS** | **唯一带完整开源 UI（Modeler/Task/Admin/IDM）的 Flowable 版本**；技术栈较旧（SB 2/3）。 |
| **Flowable 7/8 OSS** | **现代栈（SB 4 / Java 21+）+ 引擎 + REST，但无 UI**；UI 全在商业 Work/Design/Control。**本项目即此路线。** |

> **选型口诀：**
> - 要**免费 UI** + 传统 DB 引擎 → **Camunda 7 社区** 或 **Flowable 6**
> - 要**现代栈 + 嵌入式引擎**、接受无 UI → **Flowable 7/8 OSS**（本项目）
> - 要**云原生高吞吐 + 外部 worker** → **Camunda 8**

---

## 参考资料
- Camunda 7 文档：https://docs.camunda.org/manual/latest/
- Camunda 8 文档：https://docs.camunda.io/
- Flowable OSS 文档：https://www.flowable.com/open-source/docs/bpmn/
- Flowable 8.0.0 发布：https://forum.flowable.org/t/flowable-8-0-0-release/12548
- bpmn.io（开源 BPMN 建模）：https://bpmn.io/
