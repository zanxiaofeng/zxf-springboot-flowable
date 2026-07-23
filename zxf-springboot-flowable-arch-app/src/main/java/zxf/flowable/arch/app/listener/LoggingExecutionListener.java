package zxf.flowable.arch.app.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import zxf.flowable.arch.app.service.FlowableService;

@Slf4j
@RequiredArgsConstructor
@Component
public class LoggingExecutionListener implements ExecutionListener {
    private static Boolean SHORTEN_FORMAT = true;
    private final FlowableService flowableService;

    @Override
    public void notify(DelegateExecution execution) {
        log.info("{}", flowableService.executionInfoForListener(execution, SHORTEN_FORMAT));
    }
}
