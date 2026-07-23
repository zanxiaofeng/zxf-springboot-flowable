package zxf.flowable.h2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class FlowableService {
    private final ProcessEngine processEngine;

    public String instanceInfo(ProcessInstance instance) {
        return String.format("(ProcessInstanceId=%s, ProcessDefinitionId=%s, BusinessKey=%s, isSuspended=%s, TenantId=%s)",
                instance.getProcessInstanceId(), instance.getProcessDefinitionId(), instance.getBusinessKey(), instance.isSuspended(), instance.getTenantId());
    }

    public String definitionInfo(ProcessDefinition definition) {
        return String.format("(Id=%s, Category=%s, Key=%s, Version=%s, ResourceName=%s, DeploymentId=%s, isSuspended=%s, TenantId=%s)",
                definition.getId(), definition.getCategory(), definition.getKey(), definition.getVersion(), definition.getResourceName(), definition.getDeploymentId(), definition.isSuspended(), definition.getTenantId());
    }

    public String jobInfo(Job job) {
        return String.format("(Id=%s, Duedate=%s, ProcessInstanceId=%s, ProcessDefinitionId=%s, ExecutionId=%s, Retries=%d, ExceptionMessage=%s, CreateTime=%s, TenantId=%s)",
                job.getId(), job.getDuedate(), job.getProcessInstanceId(), job.getProcessDefinitionId(), job.getExecutionId(), job.getRetries(), job.getExceptionMessage(), job.getCreateTime(), job.getTenantId());
    }

    public String executionInfo(DelegateExecution execution) {
        return String.format("(Id=%s, ProcessDefinitionId=%s, ProcessInstanceId=%s, CurrentActivityName=%s, BusinessKey=%s, Variables=%s)",
                execution.getId(), execution.getProcessDefinitionId(), execution.getProcessInstanceId(), execution.getCurrentActivityName(), execution.getProcessInstanceBusinessKey(), execution.getVariables());
    }

    public String executionInfo(Execution execution) {
        return String.format("(Id=%s, ProcessInstanceId=%s)", execution.getId(), execution.getProcessInstanceId());
    }
}
