package zxf.flowable.saga.task.app2;
import org.flowable.engine.delegate.BpmnError;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;

@Slf4j
@Component
public class App2Task3Adapter implements JavaDelegate {
    private final FlowableService flowableService;

    public App2Task3Adapter(FlowableService flowableService) {
        this.flowableService = flowableService;
        log.info("ctor()");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        log.info("start, {}", flowableService.taskInfo(execution));
        log.info("threads, {}", flowableService.threadInfo(execution));

        orderServerC(execution, taskId);
        flowableService.sleep(6000);

        log.info("end  , {}", flowableService.taskInfo(execution));
    }

    private void orderServerC(DelegateExecution execution, String taskId) {
        execution.setVariable("VAR_OF_TASK3", "var of task3");

        if (flowableService.throwException() && taskId.endsWith("-3")) {
            log.error("Failed to process task: {}", taskId);
            throw new BpmnError("SAGA_FAILURE", "Failed to process task: " + taskId);
        }
    }
}