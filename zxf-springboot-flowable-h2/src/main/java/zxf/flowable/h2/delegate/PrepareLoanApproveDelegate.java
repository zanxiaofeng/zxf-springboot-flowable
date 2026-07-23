package zxf.flowable.h2.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Service;
import zxf.flowable.h2.service.FlowableService;

import java.util.Map;

/**
 * 准备贷款审批请求（对应 Camunda 版 Prepare-Loan-Approve 的 FreeMarker 模板 + 反序列化）。
 * 构造等价 JSON：{ "request": { "requestAmount": <amount>, "caseId": <caseId> }, "status": "Wait for Approval" }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareLoanApproveDelegate implements JavaDelegate {
    private final FlowableService flowableService;

    @Override
    public void execute(DelegateExecution execution) {
        Map<String, Object> loanRequestRequest = (Map<String, Object>) execution.getVariable("loanRequestRequest");
        Map<String, Object> loanRequestResponse = (Map<String, Object>) execution.getVariable("loanRequestResponse");

        Integer requestAmount = (Integer) loanRequestRequest.get("amount");
        String caseId = (String) loanRequestResponse.get("caseId");
        Map<String, Object> loanApproveRequest = Map.of(
                "request", Map.of("requestAmount", requestAmount, "caseId", caseId),
                "status", "Wait for Approval");
        execution.setVariable("loanApproveRequest", loanApproveRequest);
        log.info("PrepareLoanApprove, {}", flowableService.executionInfo(execution));
    }
}
