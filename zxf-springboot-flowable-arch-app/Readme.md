# zxf-springboot-flowable-arch-app（架构参考流程）

Payment / Form / Notification 三个静态 BPMN 流程，H2 文件库，端口 **8190**。
> Flowable OSS 不含 Camunda 那种 Web UI（无 `/camunda/app`）。本模块未引入 Flowable REST starter，故也无引擎 REST（仅 saga-app-1 在 `/process-api` 暴露）。

# H2 控制台
- http://localhost:8190/h2 （JDBC URL: `jdbc:h2:file:./flowable-h2-database`，用户 sa）

# Actuator
- http://localhost:8190/actuator
- http://localhost:8190/actuator/mappings

# 前置：先启动 mock 下游
- `java -jar zxf-springboot-flowable-arch-rest/target/zxf-springboot-flowable-arch-rest-1.0.0-SNAPSHOT.jar`（端口 8191，提供 `/task/a|b|c`、`/form/create|update|delete`）

# Payment flow（异步 + 消息 + 定时 + 调用活动 + 错误处理）
## 成功
- `GET http://localhost:8190/info/definitions/message?message=PaymentProcess.Start`
- `GET http://localhost:8190/app/payment/normal-start?orderId=1&paymentOrderCode=200&shippingRequestCode=200&shippingOrderCode=200`
- `GET http://localhost:8190/info/executions/message?message=PaymentProcess.PackageReceived`
- `GET http://localhost:8190/app/payment/package-received?executionId=<上一步查到的 executionId>`

## 超时（Expired，定时 PT3M 后抛 Error_Expired）
- `GET http://localhost:8190/app/payment/message-start?orderId=2&paymentOrderCode=200&shippingRequestCode=200&shippingOrderCode=200`
- （等待定时触发，或观察 `/info/executions`）

## 取消（Cancel）
- `GET http://localhost:8190/app/payment/message-start?orderId=3&paymentOrderCode=200&shippingRequestCode=200&shippingOrderCode=200`
- `GET http://localhost:8190/info/executions/message?message=PaymentProcess.Cancel`
- `GET http://localhost:8190/app/payment/cancel?executionId=<上一步查到的 executionId>`

> 与 Camunda 版差异：Flowable 7 移除了 `RuntimeService.correlateMessage` / `createMessageCorrelation`，消息投递改为「`/info/executions/message` 按 businessKey 查到订阅 executionId，再 `messageEventReceived`」。BPMN 中 Camunda 的 `inputOutput`（method/url/body/...）改为 method-expression + `HttpRequestDelegate` 内的操作注册表（`httpRequestDelegate.request('op', execution)`）。

# Form flow（同步创建 + 同步消息更新/删除）
- `POST http://localhost:8190/app/form/start`  body: `{"code": 200}` → 返回含 `formId` 的变量
- `POST http://localhost:8190/app/form/message?formId=<formId>&action=update`  body: `{"code": 200}`
- `POST http://localhost:8190/app/form/message?formId=<formId>&action=delete`  body: `{"code": 200}`
