# zxf-springboot-flowable-h2（Loan 审批流程）

静态 BPMN `LoanProcess`（双起始 / 条件 boundary / message 重试回路 / user task），H2 文件库，端口 **8080**。
> Flowable OSS 不含 Camunda 那种 Web UI（无 `/camunda/app`）。本模块未引入 Flowable REST starter，故也无引擎 REST。

# H2 控制台
- http://localhost:8080/h2 （JDBC URL: `jdbc:h2:file:./flowable-h2-database`，用户 sa）

# Actuator
- http://localhost:8080/actuator
- http://localhost:8080/actuator/mappings

# 流程入口
- `GET http://localhost:8080/info/definitions`
- `GET http://localhost:8080/info/instances`
- `GET http://localhost:8080/info/tasks/all`
- `GET http://localhost:8080/info/executions/message?message=LoanProcess.LoanRequestUpdated`

# Testing case 1（amount < 200000，直接审批）
- `GET http://localhost:8080/loan/request?amount=123`
- `GET http://localhost:8080/info/tasks/all` → 取 taskId
- `GET http://localhost:8080/loan/approve?taskId=<taskId>&amount=100`

# Testing case 2（amount >= 200000，触发条件 boundary → 重新提交回路）
- `GET http://localhost:8080/loan/request?amount=300000`
- `GET http://localhost:8080/info/executions/message?message=LoanProcess.LoanRequestUpdated` → 取 executionId
- `GET http://localhost:8080/loan/updated?executionId=<executionId>&amount=150000`
- `GET http://localhost:8080/info/tasks/all` → 取 taskId
- `GET http://localhost:8080/loan/approve?taskId=<taskId>&amount=100000`

> 与 Camunda 版差异：原 FreeMarker 脚本任务（`<camunda:script scriptFormat="freemarker">` + `.ftl` 模板）改为 Java delegate（`PrepareLoanRequestDelegate` / `PrepareLoanApproveDelegate`，用 ObjectMapper 构造等价 JSON）；user task 的 FreeMarker outputParameter 改为 task complete 监听器 `LoanApproveCompleteListener`。条件 boundary `${loanRequestRequest.amount >= 200000}`、message 回路拓扑保持不变。
