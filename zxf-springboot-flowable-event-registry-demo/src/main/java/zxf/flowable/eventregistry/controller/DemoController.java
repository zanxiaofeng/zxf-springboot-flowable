package zxf.flowable.eventregistry.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Demo 流程入口：启动流程、查询实例/等待状态。
 * 另含 confirm(execution) 供 BPMN service task 以 method-expression 调用。
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {
    private final ProcessEngine processEngine;

    @GetMapping("/start")
    public String start(@RequestParam String orderId) {
        log.info("start, orderId: {}", orderId);
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey("DemoProcess", orderId, new HashMap<>());
        log.info("started, orderId={}, processInstanceId={}", orderId, instance.getId());
        return instance.getId();
    }

    @GetMapping("/instances")
    public List<String> instances() {
        return processEngine.getRuntimeService().createProcessInstanceQuery().list().stream()
                .map(i -> "(id=" + i.getId() + ", businessKey=" + i.getBusinessKey()
                        + ", def=" + i.getProcessDefinitionId() + ")")
                .collect(Collectors.toList());
    }

    @GetMapping("/waiting")
    public List<String> waiting() {
        return processEngine.getRuntimeService().createExecutionQuery()
                .messageEventSubscriptionName("packageReceived").list().stream()
                .map(e -> "(executionId=" + e.getId() + ", processInstanceId=" + e.getProcessInstanceId() + ")")
                .collect(Collectors.toList());
    }

    /** 供 BPMN confirm service task 调用：${demoController.confirm(execution)} */
    public void confirm(DelegateExecution execution) {
        log.info("Package confirmed for orderId={}, trackingNumber={}",
                execution.getProcessInstanceBusinessKey(),
                execution.getVariable("trackingNumber"));
    }
}
