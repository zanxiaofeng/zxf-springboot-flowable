package zxf.flowable.arch.app.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.runtime.Execution;
import org.springframework.stereotype.Service;

/**
 * 包裹通知 delegate（对应 Camunda 版同名类）：向当前流程实例业务键关联 PaymentProcess.Notification 消息。
 * 差异：Flowable 7 移除了 createMessageCorrelation，改为按 businessKey + 消息订阅查找执行后投递。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationDelegate implements JavaDelegate {
    private final ProcessEngine processEngine;

    @Override
    public void execute(DelegateExecution execution) {
        String message = (String) execution.getVariable("message");
        log.info("notification.start, {}", message);

        String businessKey = execution.getProcessInstanceBusinessKey();
        Execution subscription = processEngine.getRuntimeService().createExecutionQuery()
                .processInstanceBusinessKey(businessKey)
                .messageEventSubscriptionName("PaymentProcess.Notification").singleResult();
        if (subscription != null) {
            processEngine.getRuntimeService().messageEventReceived("PaymentProcess.Notification", subscription.getId());
        }
        log.info("notification.end, {}", message);
    }
}
