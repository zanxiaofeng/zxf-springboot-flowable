# Flowable / Camunda 集成设计：HTTP、Spring Event、MQ

> 本文梳理 BPMN 引擎与外部系统集成的三种通道（HTTP 服务 / Spring 事件 / MQ 消息）的设计模式、各引擎差异与最佳实践，并附本项目（Flowable 7.1）的落地建议与 Event Registry + Kafka 参考实现。
> 配套示例：`docs/event-registry-example/`。

---

## 目录
- [一、HTTP 服务集成](#一http-服务集成)
- [二、Spring Event 集成](#二spring-event-集成)
- [三、MQ 集成（Kafka / RabbitMQ / JMS）](#三mq-集成kafka--rabbitmq--jms)
- [四、集成架构模式总览](#四集成架构模式总览)
- [五、最佳实践汇总](#五最佳实践汇总)
- [六、本项目现状与建议](#六本项目现状与建议)
- [七、Event Registry + Kafka 参考实现](#七event-registry--kafka-参考实现)

---

## 一、HTTP 服务集成

流程中的 service task 调用外部 REST/gRPC 微服务。

### 做法

| 做法 | Flowable | Camunda 7 | Camunda 8 |
|------|----------|-----------|-----------|
| **原生 HTTP Task** | ✅ `<serviceTask flowable:type="http">`（`flowable-http-common`），BPMN 里配 method/url/headers/body，响应映射到变量 | ❌ 社区无；可用社区 Connector | ❌（Job Worker） |
| **Service Task + JavaDelegate** | ✅ delegate 用 `RestClient`/`RestTemplate` 调下游 | ✅ 同左 | ✅ Job Worker 里调 |
| **Method-expression** | ✅ `${httpClient.request('op', execution)}` | ✅ `${httpClient.call(execution)}` | ✅ |

### Flowable 原生 HTTP Task 示例
```xml
<serviceTask id="callOrder" flowable:type="http"
             flowable:requestMethod="POST"
             flowable:requestUrl="http://order-svc/api/orders"
             flowable:requestHeaders="Content-Type: application/json&#10;X-Trace-Id: ${traceId}"
             flowable:requestBody="${orderBody}"
             flowable:responseVariableName="orderResponse"
             flowable:responseStatusCodeVariableName="orderStatus"
             flowable:async="true">
  <extensionElements>
    <flowable:failedJobRetryTimeCycle>R3/PT10S</flowable:failedJobRetryTimeCycle>
  </extensionElements>
</serviceTask>
```

### 本项目用 JavaDelegate 方式
`HttpRequestDelegate`（delegateExpression + 操作注册表）+ `CommonResponseHandler`（响应解析 + 错误分类）。保留了原 Camunda 版 `inputOutput` 的语义但改用 Flowable method-expression。

### 最佳实践
| 关注 | 做法 |
|------|------|
| **超时** | HTTP client 配 connect/read timeout（3~10s） |
| **重试** | service task `failedJobRetryTimeCycle`（R3/PT5S），应对 5xx / 超时 |
| **幂等** | 调用必须幂等（idempotency-key / 业务键去重），因 job 会重试 |
| **错误分类** | 4xx → `BpmnError`（业务错误，走 error boundary）；5xx/超时 → `RuntimeException`（技术错误，重试） |
| **熔断** | 下游加 Resilience4j `@CircuitBreaker` |
| **异步** | service task `asyncBefore`，HTTP 调用不阻塞请求线程 |
| **traceId** | header 透传 `X-Trace-Id`（= processInstanceId / MDC） |

---

## 二、Spring Event 集成

流程与 Spring `ApplicationEvent` 桥接。

### 方向 1：流程 → Spring 事件（流程里程碑通知）
```java
@Component
public class PublishEventDelegate implements JavaDelegate {
    private final ApplicationEventPublisher publisher;
    @Override
    public void execute(DelegateExecution execution) {
        publisher.publishEvent(new OrderCompletedEvent(
            execution.getProcessInstanceBusinessKey()));
    }
}
@EventListener
public void on(OrderCompletedEvent e) { /* 通知、推送、缓存失效... */ }
```

### 方向 2：Spring 事件 → 流程（外部事件推进等待中的流程）
```java
@EventListener
public void onPackageShipped(PackageShippedEvent e) {
    Execution exec = runtimeService.createExecutionQuery()
        .processInstanceBusinessKey(e.orderId())
        .messageEventSubscriptionName("PaymentProcess.PackageReceived")
        .singleResult();
    if (exec != null) {
        runtimeService.messageEventReceived("PaymentProcess.PackageReceived", exec.getId());
    }
}
```

### 最佳实践
- **Spring 事件 = 应用内通知**，不替代 BPMN message/signal（后者有持久化订阅 + 流程语义）。
- 同步事件（默认）与调用者同事务；异步用 `@Async` 或发 MQ。
- Flowable 的 **Event Registry** 可声明式桥接 Spring 事件与 BPMN（见第三节）。

---

## 三、MQ 集成（Kafka / RabbitMQ / JMS）

### Flowable Event Registry（Flowable 7/8 强项）

统一事件模型，把 BPMN 事件与 Kafka/RabbitMQ/JMS/Spring 事件**声明式桥接**：

```
外部 → Kafka topic → [Event Registry inbound channel] → 按 event key + correlation 匹配 → BPMN message catch / event start
BPMN throw → [Event Registry outbound channel] → Kafka topic → 外部
```

**声明式配置，零消费代码**：

事件定义（`packageReceived.event`）：
```json
{
  "key": "packageReceived",
  "name": "Package Received",
  "correlationParameters": [{"name": "orderId", "type": "string"}],
  "payload": [{"name": "orderId", "type": "string"},
              {"name": "trackingNumber", "type": "string"}]
}
```

Kafka 入站通道（`packageReceivedKafka.channel`）：
```json
{
  "key": "packageReceivedKafka",
  "channelType": "kafka",
  "type": "inbound",
  "channelName": "package-received-kafka",
  "topic": "package-received",
  "keyDeserializer": "org.apache.kafka.common.serialization.StringDeserializer",
  "valueDeserializer": "org.apache.kafka.common.serialization.StringDeserializer",
  "eventKey": "packageReceived"
}
```

BPMN 引用：
```xml
<intermediateCatchEvent id="waitPackage">
  <extensionElements>
    <flowable:eventEventDefinition eventRef="packageReceived" />
  </extensionElements>
</intermediateCatchEvent>
```

Kafka 消息到达 → 按 `orderId`（correlation parameter = businessKey）匹配等待中的实例 → 推进流程。**全程声明式。**

### Camunda 的 MQ 做法

| 方式 | 说明 |
|------|------|
| Service task + 生产者 | delegate 里用 Spring Kafka / AMQP 发消息 |
| MQ 消费者 → 手动关联 | `@KafkaListener` 收消息 → `messageEventReceived` / `startProcessByMessage` |
| External Task worker | worker 从 MQ 消费 → 处理 → complete external task |
| Camunda Connect | 社区/商业连接器（HTTP、Kafka） |
| Camunda 8 | Zeebe Kafka Exporter；Job Worker 里消费/生产 MQ |

### MQ 集成的 4 种架构模式

**模式 1：消费者 → 关联推进（最常见）**
```
外部 → MQ → @KafkaListener → messageEventReceived(按 businessKey) → 流程推进
```

**模式 2：Service task → 发布（fire-and-forget）**
```
流程 → service task delegate → 发 MQ → 流程继续
```

**模式 3：请求-应答（Request-Reply over MQ）**
```
流程 → 发请求到 MQ → message catch 等待 → 消费者处理 → 发回复 → message catch 触发 → 继续
```

**模式 4：Event Registry 声明式（Flowable 专属，零代码）**
```
Kafka → inbound channel → 自动关联 → BPMN；BPMN throw → outbound channel → Kafka
```

### 最佳实践
| 关注 | 做法 |
|------|------|
| **幂等消费** | MQ 会重投；消费者幂等（业务键去重 / 幂等表） |
| **按 businessKey 关联** | 不用 executionId（脆弱）；用 orderId 等业务键 |
| **DLQ 分离** | MQ 死信（消费失败）vs BPMN 死信（job 失败）分开 |
| **事务边界** | MQ 消费 + 流程推进尽量不跨分布式事务；用幂等 + 最终一致 |
| **消息契约** | MQ schema 版本化（Avro/Protobuf schema registry） |
| **背压** | 消费速率 > 处理速率时积压 job；监控 |

---

## 四、集成架构模式总览

| 模式 | 通道 | 适合 |
|------|------|------|
| **同步编排** | HTTP service task | 请求-响应、实时、短耗时（payment, shipping） |
| **异步编排** | MQ + message catch / event catch | 长耗时、解耦、可重试（审批、风控） |
| **事件驱动触发** | MQ/Event Registry → start/correlate | 外部系统触发流程 |
| **事件发射** | service task → MQ/Spring event | 流程里程碑通知外部 |
| **External Worker** | External Task / Job Worker | 业务逻辑在独立微服务（Camunda 模式） |

> 混合使用：一个流程里同步步骤用 HTTP service task，异步步骤用 message/event catch + MQ。本项目 PaymentProcess 即是混合：HTTP 调下游 + message catch 等待（PackageReceived/Cancel/InfoUpdate）。

---

## 五、最佳实践汇总

| 关注 | HTTP | Spring Event | MQ |
|------|------|-------------|-----|
| **幂等** | idempotency-key | — | 业务键去重 |
| **重试** | failedJobRetryTimeCycle | — | MQ 重投 + 幂等 |
| **超时** | client timeout | — | consumer timeout |
| **错误分类** | 4xx→BpmnError, 5xx→retry | — | DLQ |
| **关联键** | — | businessKey | businessKey |
| **熔断** | Resilience4j | — | — |
| **traceId** | header 透传 | MDC | header/property |
| **事务** | 不跨事务 | 默认同事务 | 不跨分布式事务 |

---

## 六、本项目现状与建议

| 集成 | 现状 | 建议 |
|------|------|------|
| **HTTP** | ✅ `HttpRequestDelegate` + 注册表 + `CommonResponseHandler` | 加超时/熔断（RestClient + Resilience4j）、traceId header |
| **Spring Event** | ❌ | 加：关键里程碑（流程结束/补偿完成）发 `ApplicationEvent` |
| **MQ** | ❌ | **加 Flowable Event Registry + Kafka**（见第七节），把 message catch 从 controller 手动投递升级为 MQ 声明式关联 |

---

## 七、Event Registry + Kafka 参考实现

见 `docs/event-registry-example/` 目录，含：
- `packageReceived.event` — 事件定义
- `packageReceivedKafka.channel` — Kafka 入站通道
- `packageReceivedSpring.channel` — Spring 事件入站通道（无需 broker，可测试）
- `docker-compose-kafka.yml` — Kafka + Zookeeper 容器
- `EventRegistryDemo.bpmn` — 示例 BPMN（用 `flowable:eventEventDefinition`）
- `README.md` — 启用步骤与说明

> 这个示例把 PaymentProcess 的 PackageReceived 从「controller 手动 `messageEventReceived`」升级为「Kafka/Spring event 声明式关联」，更贴近生产微服务架构。
