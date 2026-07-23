package zxf.flowable.h2.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 贷款审批完成监听器（对应 Camunda 版 UserTask_Loan-Approve 的 FreeMarker outputParameter）。
 * 在任务完成时构造审批响应变量 loanApproveResponse：
 * { "request": { "requestAmount": <amount>, "caseId": <caseId> }, "status": "Approved" }
 */
@Slf4j
@Component
public class LoanApproveCompleteListener implements TaskListener {
    @Override
    public void notify(DelegateTask delegateTask) {
        Map<String, Object> loanRequestRequest = (Map<String, Object>) delegateTask.getVariable("loanRequestRequest");
        Map<String, Object> loanRequestResponse = (Map<String, Object>) delegateTask.getVariable("loanRequestResponse");
        Object requestAmount = loanRequestRequest == null ? null : loanRequestRequest.get("amount");
        Object caseId = loanRequestResponse == null ? null : loanRequestResponse.get("caseId");

        Map<String, Object> response = new HashMap<>();
        response.put("request", Map.of("requestAmount", requestAmount, "caseId", caseId));
        response.put("status", "Approved");
        delegateTask.setVariable("loanApproveResponse", response);
        log.info("LoanApprove completed, loanApproveResponse={}", response);
    }
}
