package zxf.flowable.h2.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Service;
import zxf.flowable.h2.service.FlowableService;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class LoanRequestDelegate implements JavaDelegate {
    private final FlowableService flowableService;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("loanRequestResponse", generateResponse());
        log.info("{}", flowableService.executionInfo(execution));
    }

    private Map<String, Object> generateResponse() {
        return Collections.singletonMap("caseId", UUID.randomUUID().toString());
    }
}