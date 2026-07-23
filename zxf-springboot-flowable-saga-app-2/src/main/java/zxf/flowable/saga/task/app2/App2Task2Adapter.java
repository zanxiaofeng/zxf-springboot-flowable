package zxf.flowable.saga.task.app2;
import org.flowable.engine.delegate.BpmnError;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;

@Slf4j
@Component
public class App2Task2Adapter implements JavaDelegate {
    private final FlowableService flowableService;

    public App2Task2Adapter(FlowableService flowableService) {
        this.flowableService = flowableService;
        log.info("ctor()");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        log.info("start, {}", flowableService.taskInfo(execution));
        log.info("threads, {}", flowableService.threadInfo(execution));

        orderServerB(execution, taskId);
        flowableService.sleep(5000);

        log.info("end  , {}", flowableService.taskInfo(execution));
    }


    private void orderServerB(DelegateExecution execution, String taskId) {
        execution.setVariable("VAR_OF_TASK2", "var of task2");

        if (flowableService.throwException() && taskId.endsWith("-2")) {
            log.error("Failed to process task: {}", taskId);
            throw new BpmnError("SAGA_FAILURE", "Failed to process task: " + taskId);
        }
    }
}