package zxf.flowable.arch.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.*;
import zxf.flowable.arch.app.controller.model.ProcessParameters;
import zxf.flowable.arch.app.service.FlowableService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app")
public class AppController {
    private final ProcessEngine processEngine;
    private final FlowableService flowableService;

    @PostMapping("/normal-start")
    public String normalStart(@RequestParam String processKey, @RequestBody ProcessParameters processParameters) {
        log.info("normalStart, {}, {}", processKey, processParameters);
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(processKey, processParameters.getBusinessKey(), processParameters.getVariables());
        return processInstance.getProcessInstanceId();
    }

    @PostMapping("/message-start")
    public String messageStart(@RequestParam String messageId, @RequestBody ProcessParameters processParameters) {
        log.info("messageStart, {}, {}", messageId, processParameters);
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByMessage(messageId, processParameters.getBusinessKey(), processParameters.getVariables());
        return processInstance.getProcessInstanceId();
    }

    @PostMapping("/message-received")
    public void messageReceived(@RequestParam String messageId, @RequestBody ProcessParameters processParameters) {
        log.info("messageReceived, {}, {}", messageId, processParameters);
        // Flowable 7 无 RuntimeService.correlateMessage：按 businessKey + 消息订阅查找执行后投递
        Execution execution = processEngine.getRuntimeService().createExecutionQuery()
                .processInstanceBusinessKey(processParameters.getBusinessKey())
                .messageEventSubscriptionName(messageId).singleResult();
        if (execution != null) {
            processEngine.getRuntimeService().messageEventReceived(messageId, execution.getId(), processParameters.getVariables());
        }
    }
}
