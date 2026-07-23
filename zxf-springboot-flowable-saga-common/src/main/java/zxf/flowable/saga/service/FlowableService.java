package zxf.flowable.saga.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Saga 通用服务（对应 Camunda 版的 CamundaService）。
 *
 * <p>提供配置访问、重试探测（{@link #isFirstExecution} / {@link #isLastExecution}）、线程追踪与各类信息格式化。
 *
 * <p>差异说明：
 * <ul>
 *   <li>Flowable 无 deployment-aware job executor，{@code registerDeploymentForJobExecutor} 相关行为已改为 no-op + 日志。</li>
 *   <li>重试周期从部署后的 BpmnModel 中读取 {@code failedJobRetryTimeCycle} 扩展元素；剩余次数从 ManagementService 查询当前 job 的 retries。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class FlowableService {
    @Value("${saga.app-name}")
    private String appName;
    @Value("${saga.re-deploy}")
    private boolean sagaRedeploy;
    @Value("${saga.async-before}")
    private boolean asyncBefore;
    @Value("${saga.async-after}")
    private boolean asyncAfter;
    @Value("${saga.register-deployment}")
    private boolean registerDeployment;
    @Value("${saga.throw-exception}")
    private boolean throwException;

    private final ProcessEngine processEngine;

    public String appName() {
        return appName;
    }

    public boolean sagaRedeploy() {
        return sagaRedeploy;
    }

    public boolean asyncBefore() {
        return asyncBefore;
    }

    public boolean asyncAfter() {
        return asyncAfter;
    }

    public boolean registerDeployment() {
        return registerDeployment;
    }

    public boolean throwException() {
        return throwException;
    }

    /**
     * 模拟业务处理耗时。Flowable 的 JavaDelegate.execute 不声明 throws，
     * 故适配器不能直接抛 InterruptedException，统一用此工具方法包装。
     */
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public boolean isFirstExecution(DelegateExecution execution) {
        int totalRetryCount = getTotalRetryCount(execution);
        int leftRetryTimes = getLeftRetryTimes(execution);

        boolean firstExecution = totalRetryCount == leftRetryTimes;
        log.info("check, firstCall={}, total={}, rest={}, {}", firstExecution, totalRetryCount, leftRetryTimes, taskInfo(execution));
        return firstExecution;
    }

    public boolean isLastExecution(DelegateExecution execution) {
        int totalRetryCount = getTotalRetryCount(execution);
        int leftRetryTimes = getLeftRetryTimes(execution);

        boolean lastExecution = totalRetryCount == 1 || leftRetryTimes == 1;
        log.info("check, lastCall={}, total={}, rest={}, {}", lastExecution, totalRetryCount, leftRetryTimes, taskInfo(execution));
        return lastExecution;
    }

    public String threadInfo(DelegateExecution execution) {
        String threads = (String) execution.getVariable("THREADS");
        threads = (threads == null ? "" : threads + ", ") + execution.getCurrentActivityName() + "@" + Thread.currentThread().getName() + "@" + appName;
        execution.setVariable("THREADS", threads);
        return threads;
    }

    public String instanceInfo(ProcessInstance instance) {
        return String.format("(ProcessInstanceId=%s, %s, ProcessDefinitionId=%s, BusinessKey=%s, isSuspended=%s, TenantId=%s)",
                instance.getId(), instance.getProcessInstanceId(), instance.getProcessDefinitionId(),
                instance.getBusinessKey(), instance.isSuspended(), instance.getTenantId());
    }

    public String definitionInfo(ProcessDefinition definition) {
        return String.format("(Id=%s, Category=%s, Key=%s, Version=%s, ResourceName=%s, DeploymentId=%s, isSuspended=%s, TenantId=%s)",
                definition.getId(), definition.getCategory(), definition.getKey(), definition.getVersion(), definition.getResourceName(),
                definition.getDeploymentId(), definition.isSuspended(), definition.getTenantId());
    }

    public String jobInfo(Job job) {
        return String.format("(Id=%s, Duedate=%s, ProcessInstanceId=%s, ProcessDefinitionId=%s, ExecutionId=%s, Retries=%d, ExceptionMessage=%s, CreateTime=%s, TenantId=%s)",
                job.getId(), job.getDuedate(), job.getProcessInstanceId(), job.getProcessDefinitionId(),
                job.getExecutionId(), job.getRetries(), job.getExceptionMessage(), job.getCreateTime(), job.getTenantId());
    }

    public String taskInfo(DelegateExecution execution) {
        String taskId = (String) execution.getVariable("task-id");
        return String.format("{%s, %s, %s, %s}", execution.getCurrentActivityName(), taskId, execution.getId(), execution.getProcessDefinitionId());
    }

    /**
     * Flowable 无 deployment-aware，此方法仅为保留接口兼容，记录日志。
     */
    public void registerDeploymentForJobExecutor(String deploymentId) {
        log.info("registerDeploymentForJobExecutor is a no-op in Flowable (Flowable uses lock-based job distribution). DeploymentId={}", deploymentId);
    }

    private int getLeftRetryTimes(DelegateExecution execution) {
        Job job = processEngine.getManagementService().createJobQuery()
                .executionId(execution.getId()).singleResult();
        if (job == null) {
            throw new IllegalStateException("This task is not a Job. Please be noted that only async task is a job)");
        }

        return job.getRetries();
    }

    private int getTotalRetryCount(DelegateExecution execution) {
        BpmnModel bpmnModel = processEngine.getRepositoryService().getBpmnModel(execution.getProcessDefinitionId());
        FlowElement element = bpmnModel.getMainProcess().getFlowElement(execution.getCurrentActivityId());
        if (element instanceof Activity) {
            String retryCycle = ((Activity) element).getFailedJobRetryTimeCycleValue();
            if (retryCycle != null) {
                int count = parseRetryTimes(retryCycle);
                return count == 0 ? 1 : count;
            }
        }
        throw new IllegalStateException("Cannot find failedJobRetryTimeCycle on activity: " + execution.getCurrentActivityId());
    }

    private Integer parseRetryTimes(String failedJobRetryTimeCycle) {
        return Integer.parseInt(failedJobRetryTimeCycle.substring(1, failedJobRetryTimeCycle.indexOf("/")));
    }
}
