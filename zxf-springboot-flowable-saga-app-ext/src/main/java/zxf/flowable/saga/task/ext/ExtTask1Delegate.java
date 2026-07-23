package zxf.flowable.saga.task.ext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.service.FlowableService;

/**
 * Ext-Task 1（对应 Camunda 版 LocalExternalTaskWorker 中 ext-topic-1 的处理逻辑）。
 * 原由外部 worker 在自有线程池轮询执行；现改为 Flowable async service task 的 JavaDelegate，由 job executor 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtTask1Delegate implements JavaDelegate {
    private final FlowableService flowableService;

    @Override
    public void execute(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        log.info("start, {}", flowableService.taskInfo(execution));
        log.info("threads, {}", flowableService.threadInfo(execution));

        flowableService.sleep(2000);

        log.info("end  , {}", flowableService.taskInfo(execution));
    }
}
