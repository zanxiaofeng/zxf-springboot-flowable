package zxf.flowable.saga.task.app3;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;

@Slf4j
@Component
public class App3Task3Adapter implements JavaDelegate {
    private final FlowableService flowableService;

    public App3Task3Adapter(FlowableService flowableService) {
        this.flowableService = flowableService;
        log.info("ctor()");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        boolean isFirstExecution = flowableService.isFirstExecution(execution);
        boolean isLastExecution = flowableService.isLastExecution(execution);
        log.info("start, {}, isFirstExecution={}, isLastExecution={}", flowableService.taskInfo(execution), isFirstExecution, isLastExecution);
        log.info("threads, {}", flowableService.threadInfo(execution));

        if (flowableService.throwException() && taskId.endsWith("-3")) {
            log.error("Failed to process task: {}", taskId);
            throw new RuntimeException("Failed to process task: " + taskId);
            //After this, all engine database change in this method  will be rollback(VARS...).
        }

        flowableService.sleep(8000);

        log.info("end  , {}", flowableService.taskInfo(execution));
    }
}