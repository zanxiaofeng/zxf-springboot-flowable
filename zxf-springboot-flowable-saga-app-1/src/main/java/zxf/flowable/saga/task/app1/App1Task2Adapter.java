package zxf.flowable.saga.task.app1;
import org.flowable.engine.delegate.BpmnError;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;
import zxf.flowable.saga.service.OrderService;

import java.util.UUID;

@Slf4j
@Component
public class App1Task2Adapter implements JavaDelegate {
    private final FlowableService flowableService;
    private final OrderService orderService;

    public App1Task2Adapter(FlowableService flowableService, OrderService orderService) {
        this.flowableService = flowableService;
        this.orderService = orderService;
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

        String orderId = UUID.randomUUID().toString();
        if (!orderService.createOrder(orderId)) {
            log.error("Failed to create order: {}, taskId: {}", orderId, taskId);
            throw new BpmnError("SAGA_FAILURE", "Failed to create order: " + orderId + ", taskId: " + taskId);
        }
        log.info("createOrder, {}, {}", execution.getVariable("task-id"), orderId);
        execution.setVariable("ORDER_ID", orderId);
    }
}