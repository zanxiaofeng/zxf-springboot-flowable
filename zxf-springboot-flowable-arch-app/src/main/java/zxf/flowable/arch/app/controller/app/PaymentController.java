package zxf.flowable.arch.app.controller.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/payment")
public class PaymentController {
    private final ProcessEngine processEngine;

    @GetMapping("/normal-start")
    public Map<String, Object> normalStart(@RequestParam String orderId, @RequestParam String paymentOrderCode, @RequestParam String shippingRequestCode, @RequestParam String shippingOrderCode) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("OrderId", orderId);
        variables.put("paymentOrderCode", paymentOrderCode);
        variables.put("shippingRequestCode", shippingRequestCode);
        variables.put("shippingOrderCode", shippingOrderCode);

        log.info("normalStart, {}, {}", orderId, variables);
        // Flowable 无 executeWithVariablesInReturn：start 后 getVariables 取回
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("PaymentProcess", orderId, variables);
        Map<String, Object> returnVariables = new HashMap<>(processEngine.getRuntimeService().getVariables(processInstance.getId()));
        returnVariables.put("ProcessInstanceId", processInstance.getProcessInstanceId());
        return returnVariables;
    }

    @GetMapping("/message-start")
    public String messageStart(@RequestParam String orderId, @RequestParam String paymentOrderCode, @RequestParam String shippingRequestCode, @RequestParam String shippingOrderCode) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("OrderId", orderId);
        variables.put("paymentOrderCode", paymentOrderCode);
        variables.put("shippingRequestCode", shippingRequestCode);
        variables.put("shippingOrderCode", shippingOrderCode);

        log.info("messageStart, {}, {}", orderId, variables);
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByMessage("PaymentProcess.Start", orderId, variables);
        return processInstance.getProcessInstanceId();
    }

    @GetMapping("/info-update")
    public void paymentInfoUpdate(@RequestParam String orderId) {
        log.info("paymentInfoUpdate, {}", orderId);
        // Flowable 7 无 correlateMessage：按 businessKey + 消息订阅查找执行后投递
        Execution execution = processEngine.getRuntimeService().createExecutionQuery()
                .processInstanceBusinessKey(orderId).messageEventSubscriptionName("PaymentProcess.InfoUpdate").singleResult();
        if (execution != null) {
            processEngine.getRuntimeService().messageEventReceived("PaymentProcess.InfoUpdate", execution.getId());
        }
    }

    @GetMapping("/package-received")
    public void packageReceived(@RequestParam String executionId) {
        log.info("packageReceived, {}", executionId);
        processEngine.getRuntimeService().messageEventReceived("PaymentProcess.PackageReceived", executionId);
    }

    @GetMapping("/cancel")
    public void paymentCancel(@RequestParam String executionId) {
        log.info("paymentCancel, {}", executionId);
        processEngine.getRuntimeService().messageEventReceived("PaymentProcess.Cancel", executionId);
    }
}
