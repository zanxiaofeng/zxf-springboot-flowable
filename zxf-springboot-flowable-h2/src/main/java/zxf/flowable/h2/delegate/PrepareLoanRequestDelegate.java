package zxf.flowable.h2.delegate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Service;
import zxf.flowable.h2.service.FlowableService;

import java.util.Map;

/**
 * 准备贷款请求（对应 Camunda 版 Prepare-Loan-Request 的 FreeMarker 脚本任务 + 反序列化）。
 * Flowable 无 inputOutput 映射且 FreeMarker 非原生 JSR-223，故改为 Java delegate 直接构造请求体。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareLoanRequestDelegate implements JavaDelegate {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FlowableService flowableService;

    @Override
    public void execute(DelegateExecution execution) {
        Integer amount = (Integer) execution.getVariable("amount");
        Map<String, Object> loanRequestRequest = Map.of("amount", amount);
        execution.setVariable("loanRequestRequest", loanRequestRequest);
        try {
            execution.setVariable("loanRequestRequestString", OBJECT_MAPPER.writeValueAsString(loanRequestRequest));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        log.info("PrepareLoanRequest, {}", flowableService.executionInfo(execution));
    }
}
