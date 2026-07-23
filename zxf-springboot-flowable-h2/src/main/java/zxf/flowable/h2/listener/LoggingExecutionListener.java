package zxf.flowable.h2.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import zxf.flowable.h2.service.FlowableService;

@Slf4j
@RequiredArgsConstructor
@Component
public class LoggingExecutionListener implements ExecutionListener {
    private final FlowableService flowableService;

    @Override
    public void notify(DelegateExecution execution) {
        log.info("{}", flowableService.executionInfo(execution));
    }
}
