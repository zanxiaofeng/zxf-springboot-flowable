package zxf.flowable.saga.task.app1;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;

@Slf4j
@Component
public class App1Task2UndoAdapter implements JavaDelegate {
    private final FlowableService flowableService;

    public App1Task2UndoAdapter(FlowableService flowableService) {
        this.flowableService = flowableService;
        log.info("ctor()");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        log.info("start, {}", flowableService.taskInfo(execution));
        log.info("threads, {}", flowableService.threadInfo(execution));

        cancelServiceB(execution, taskId);
        flowableService.sleep(3000);

        log.info("end  , {}", flowableService.taskInfo(execution));
    }

    private void cancelServiceB(DelegateExecution execution, String taskId) {
        String varOfTask1 = (String) execution.getVariable("VAR_OF_TASK1");
        String varOfTask2 = (String) execution.getVariable("VAR_OF_TASK2");
        String varOfTask3 = (String) execution.getVariable("VAR_OF_TASK3");
        log.info("vars, taskId={}, VAR_OF_TASK1={}, VAR_OF_TASK2={}, VAR_OF_TASK3={}", taskId, varOfTask1, varOfTask2, varOfTask3);

        String orderId = (String) execution.getVariable("ORDER_ID");
        log.info("vars, taskId={}, ORDER_ID={}", taskId, orderId);
    }
}