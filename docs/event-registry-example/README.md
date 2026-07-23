# Event Registry + Kafka 参考示例

本目录演示如何把 Flowable 的 **Event Registry**（统一事件模型）与 **Kafka / Spring Event** 集成，实现 BPMN 流程的声明式事件关联（零消费代码）。

## 与本项目现有方式的对比

| | 现有方式（手动投递） | Event Registry 方式（声明式） |
|--|----------------------|------------------------------|
| **消息投递** | controller 调 `messageEventReceived(name, executionId)` | Kafka/Spring event → inbound channel → 自动按 businessKey 关联 |
| **BPMN 写法** | `<messageEventDefinition messageRef="...">` | `<flowable:eventEventDefinition eventRef="packageReceived">` |
| **消费代码** | 需写 executionQuery + messageEventReceived | **零代码**（声明式 JSON） |
| **适合** | 同步投递、HTTP 触发 | 异步、MQ 驱动、生产微服务 |

## 文件说明

| 文件 | 说明 |
|------|------|
| `packageReceived.event` | 事件定义：key=`packageReceived`，correlation=`orderId`，payload=orderId+trackingNumber |
| `packageReceivedKafka.channel` | Kafka 入站通道：监听 topic `package-received`，按 `orderId` 关联到流程 |
| `packageReceivedSpring.channel` | Spring 事件入站通道：监听名为 `packageReceivedEvent` 的 Spring ApplicationEvent（无需 broker，可快速测试） |
| `docker-compose-kafka.yml` | Kafka + Zookeeper 容器 |
| `EventRegistryDemo.bpmn` | 示例流程：Start → 等待 packageReceived 事件 → End |

## 启用步骤

### 方式一：Spring Event 通道（无需 Kafka，最快验证）

1. 把 `packageReceived.event` 和 `packageReceivedSpring.channel` 复制到 arch-app 的 `src/main/resources/events/` 目录（Flowable Event Registry 自动扫描该目录部署）。

2. 把 `EventRegistryDemo.bpmn` 复制到 arch-app 的 `src/main/resources/bpmn/` 目录。

3. 启动 arch-app，确认 Event Registry 部署了事件 + 通道（日志可见）。

4. 启动一个流程实例（businessKey = orderId）：
   ```bash
   curl -X POST "http://localhost:8190/app/normal-start?processKey=EventRegistryDemo" \
        -H "Content-Type: application/json" \
        -d '{"businessKey":"ORDER-001","variables":{}}'
   ```
   流程到达 `waitForPackage` 后等待。

5. 发布 Spring 事件触发关联：
   ```java
   // 在 arch-app 里注入 ApplicationEventPublisher，发布事件
   publisher.publishEvent(new PackageReceivedEvent("ORDER-001", "TRK-123"));
   // 事件名 = "packageReceivedEvent"（与 channel 定义中的 eventName 匹配）
   // orderId = "ORDER-001" → 按 businessKey 关联到等待中的实例 → 流程推进
   ```

### 方式二：Kafka 通道（生产模式）

1. 启动 Kafka：
   ```bash
   docker compose -f docs/event-registry-example/docker-compose-kafka.yml up -d
   ```

2. 在 arch-app `pom.xml` 添加 Kafka 依赖：
   ```xml
   <dependency>
     <groupId>org.flowable</groupId>
     <artifactId>flowable-kafka</artifactId>
     <version>${flowable.version}</version>
   </dependency>
   ```

3. 把 `packageReceived.event` 和 `packageReceivedKafka.channel` 复制到 `src/main/resources/events/`。

4. 把 `EventRegistryDemo.bpmn` 复制到 `src/main/resources/bpmn/`。

5. 配置 Kafka 连接（`application.yaml`）：
   ```yaml
   flowable:
     eventregistry:
       kafka:
         bootstrap-servers: localhost:9092
   ```

6. 启动 arch-app，启动流程实例（同方式一第 4 步）。

7. 向 Kafka topic 发消息触发关联：
   ```bash
   # JSON 消息体需含 orderId（correlation parameter）和 eventKey
   kafka-console-producer --topic package-received --bootstrap-server localhost:9092
   # 输入：
   # {"eventKey":"packageReceived","orderId":"ORDER-001","trackingNumber":"TRK-123"}
   ```
   Flowable Event Registry 自动消费 → 按 `orderId` 关联 → 流程推进。

## 扩展到 PaymentProcess

要把本项目 PaymentProcess 的 PackageReceived 从手动投递改为 Event Registry：

1. 修改 `PaymentProcess.bpmn`，把 `Event_Wait-for-Package-Receive` 的 `<messageEventDefinition messageRef="..."/>` 改为 `<flowable:eventEventDefinition eventRef="packageReceived"/>`。
2. 部署 `packageReceived.event` + Kafka/Spring channel 到 `events/` 目录。
3. 删除 PaymentController 里的 `packageReceived` 端点（不再需要手动投递）。

## 注意

- Event Registry 的自动部署目录通常为 `classpath*:/events/`（扫描 `*.event`、`*.channel`）。如未自动部署，可通过 `flowable.eventregistry.resource-location` 配置。
- `correlationKeyExpression`（如 `${orderId}`）定义了用事件的哪个字段与流程的 `businessKey` 匹配。确保启动流程时 businessKey = orderId。
- Kafka channel 需要 `flowable-kafka` 依赖；Spring channel 需 `flowable-spring-boot-starter`（含 Event Registry）。
- 消费者必须**幂等**（Kafka 会重投）；按 businessKey 关联，不用 executionId。
