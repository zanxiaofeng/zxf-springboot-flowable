package zxf.flowable.saga.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zxf.flowable.saga.service.FlowableService;

import java.util.Collections;
import java.util.List;
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

    @GetMapping("/instances")
    public List<String> instances() {
        log.info("instances");
        List<ProcessInstance> instances = processEngine.getRuntimeService().createProcessInstanceQuery().list();
        return instances.stream().map(flowableService::instanceInfo).collect(Collectors.toList());
    }

    /**
     * 异步 job 列表（saga 的 service task 为 async，对应此类 job）。
     * 注意：R3/PT5S 这类带重试周期的失败 job 会转为 timer job，可通过 {@code createTimerJobQuery()} 查看，此处从略。
     */
    @GetMapping("/jobs/all")
    public List<String> allJobs(@RequestParam(required = false) String processDefinitionId) {
        log.info("allJobs");
        List<Job> allJobs = baseJobQuery(processDefinitionId).list();
        return allJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/failed")
    public List<String> failedJobs(@RequestParam(required = false) String processDefinitionId) {
        log.info("failedJobs");
        List<Job> failedJobs = baseJobQuery(processDefinitionId).withException().list();
        return failedJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/active")
    public List<String> activeJobs(@RequestParam(required = false) String processDefinitionId) {
        log.info("activeJobs");
        // Flowable JobQuery 无 active() 过滤（无 suspended job 概念），此处按 retries 倒序返回全部 job。
        List<Job> activeJobs = baseJobQuery(processDefinitionId).orderByJobRetries().desc().list();
        return activeJobs.stream().map(flowableService::jobInfo).collect(Collectors.toList());
    }

    @GetMapping("/jobs/retry")
    public List<String> retryJobs(@RequestParam(required = false) String processDefinitionId) {
        log.info("retryJobs");
        // Flowable JobQuery 无 withRetriesLeft()，这里在内存中过滤 retries > 0 的 job。
        return baseJobQuery(processDefinitionId).orderByJobRetries().desc().list().stream()
                .filter(job -> job.getRetries() > 0)
                .map(flowableService::jobInfo)
                .collect(Collectors.toList());
    }

    /**
     * Flowable 无 deployment-aware job executor 概念（采用基于锁的 job 分发），故此端点返回全部部署的流程定义并附说明。
     */
    @GetMapping("/deployments/registered")
    public List<String> registeredDeployments() {
        log.info("registeredDeployments (Flowable has no deployment-aware job executor; returning all definitions)");
        return processEngine.getRepositoryService().createProcessDefinitionQuery().list().stream()
                .map(d -> flowableService.definitionInfo(d) + " [Flowable: lock-based job distribution, no deployment-awareness]")
                .collect(Collectors.toList());
    }

    /**
     * 原项目使用 Camunda External Task；Flowable 无此概念，saga-app-ext 已改为异步 service task + job executor。
     */
    @GetMapping("/external-tasks")
    public List<String> externalTasks() {
        log.info("externalTasks (Flowable has no external task; saga-app-ext uses async service tasks)");
        return Collections.singletonList("Flowable has no external task concept. See saga-app-ext (async service tasks + job executor).");
    }

    private JobQuery baseJobQuery(String processDefinitionId) {
        JobQuery jobQuery = processEngine.getManagementService().createJobQuery();
        if (processDefinitionId != null) {
            jobQuery = jobQuery.processDefinitionId(processDefinitionId);
        }
        return jobQuery;
    }
}
