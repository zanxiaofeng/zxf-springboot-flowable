package zxf.flowable.arch.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobQuery;
import org.flowable.task.api.Task;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zxf.flowable.arch.app.service.FlowableService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/info")
public class InfoController {
    private final ProcessEngine processEngine;
    private final FlowableService flowableService;

    @GetMapping("/definitions")
    public List<String> definitions() {
        log.info("definitions");
        List<ProcessDefinition> definitions = processEngine.getRepositoryService().createProcessDefinitionQuery().list();
        return definitions.stream().map(flowableService::definitionInfo).collect(Collectors.toList());
    }

    @GetMapping("/definitions/message")
    public List<String> definitionsByMessage(@RequestParam String message) {
        log.info("definitionsByMessage");
        List<ProcessDefinition> definitions = processEngine.getRepositoryService().createProcessDefinitionQuery().messageEventSubscriptionName(message).list();
        return definitions.stream().map(flowableService::definitionInfo).collect(Collectors.toList());
    }

    @GetMapping("/instances")
    public List<String> instances() {
        log.info("instances");
        List<ProcessInstance> instances = processEngine.getRuntimeService().createProcessInstanceQuery().list();
        return instances.stream().map(flowableService::instanceInfo).collect(Collectors.toList());
    }

    @GetMapping("/executions")
    public List<String> executions() {
        log.info("executions");
        List<Execution> instances = processEngine.getRuntimeService().createExecutionQuery().list();
        return instances.stream().map(flowableService::executionInfo).collect(Collectors.toList());
    }

    @GetMapping("/executions/message")
    public List<String> executionsByMessage(@RequestParam String message) {
        log.info("executionsByMessage");
        List<Execution> instances = processEngine.getRuntimeService().createExecutionQuery().messageEventSubscriptionName(message).list();
        return instances.stream().map(flowableService::executionInfo).collect(Collectors.toList());
    }

    @GetMapping("/tasks/all")
    public List<String> allTasks() {
        log.info("allTasks");
        List<Task> tasks = processEngine.getTaskService().createTaskQuery().processDefinitionKey("PaymentProcess").list();
        return tasks.stream().map(Task::toString).collect(Collectors.toList());
    }

    @GetMapping("/jobs/all")
    public List<String> allJobs() {
        log.info("allJobs");
        JobQuery jobQuery = processEngine.getManagementService().createJobQuery().processDefinitionKey("PaymentProcess");
        List<Job> allJobs = jobQuery.list();
        return allJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/failed")
    public List<String> failedJobs() {
        log.info("failedJobs");
        JobQuery jobQuery = processEngine.getManagementService().createJobQuery().processDefinitionKey("PaymentProcess");
        List<Job> failedJobs = jobQuery.withException().list();
        return failedJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/active")
    public List<String> activeJobs() {
        log.info("activeJobs");
        // Flowable JobQuery 无 active() 过滤，按 retries 倒序返回。
        JobQuery jobQuery = processEngine.getManagementService().createJobQuery().processDefinitionKey("PaymentProcess");
        List<Job> activeJobs = jobQuery.orderByJobRetries().desc().list();
        return activeJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/retry")
    public List<String> retryJobs() {
        log.info("retryJobs");
        // Flowable JobQuery 无 withRetriesLeft()，内存过滤 retries > 0。
        JobQuery jobQuery = processEngine.getManagementService().createJobQuery().processDefinitionKey("PaymentProcess");
        return jobQuery.orderByJobRetries().desc().list().stream()
                .filter(job -> job.getRetries() > 0)
                .map(flowableService::jobInfo)
                .collect(Collectors.toList());
    }

    @GetMapping("/deployments/registered")
    public List<String> registeredDeployments() {
        log.info("registeredDeployments (Flowable has no deployment-aware job executor; returning all definitions)");
        return processEngine.getRepositoryService().createProcessDefinitionQuery().list().stream()
                .map(flowableService::definitionInfo)
                .collect(Collectors.toList());
    }
}
