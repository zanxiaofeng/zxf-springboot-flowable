package zxf.flowable.arch.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Component;
import zxf.flowable.arch.app.exception.BusinessErrorException;
import zxf.flowable.arch.app.exception.BusinessErrors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * arch-app 通用服务（对应 Camunda 版 CamundaService）。
 *
 * <p>差异：
 * <ul>
 *   <li>Camunda 的 {@code executeWithVariablesInReturn()} / {@code VariableMap} / {@code ProcessInstanceWithVariables}
 *   在 Flowable 无直接等价；改为 start 后 {@code runtimeService.getVariables(processInstanceId)} 取回，返回 {@link Map}。</li>
 *   <li>DelegateExecution 的 Camunda 专有方法（getBusinessKey/getActivityInstanceId/getVariableScopeKey 等）改为 Flowable 等价或精简。</li>
 * </ul>
 */
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

    public String executionInfo(Execution execution) {
        return String.format("(Id=%s, ProcessInstanceId=%s)", execution.getId(), execution.getProcessInstanceId());
    }

    public String executionInfoForService(DelegateExecution execution, Boolean shortenFormat) {
        return String.format("(Id=%s, BusinessKey=%s, CurrentActivityName=%s, Variables=%s)",
                execution.getId(), execution.getProcessInstanceBusinessKey(), execution.getCurrentActivityName(),
                Boolean.TRUE.equals(shortenFormat) ? execution.getVariableNames() : execution.getVariables());
    }

    public String executionInfoForListener(DelegateExecution execution, Boolean shortenFormat) {
        return String.format("CurrentActivityName=%s, EventName=%s, BusinessKey=%s, (Id=%s, ParentId=%s, ProcessDefinitionId=%s, ProcessInstanceId=%s, Variables=%s)",
                execution.getCurrentActivityName(), execution.getEventName(), execution.getProcessInstanceBusinessKey(),
                execution.getId(), execution.getParentId(),
                execution.getProcessDefinitionId(), execution.getProcessInstanceId(),
                Boolean.TRUE.equals(shortenFormat) ? execution.getVariableNames() : execution.getVariables());
    }

    public ProcessInstance startProcess(String processDefinitionKey, String businessKey, Map<String, Object> processVariables) throws BusinessErrorException {
        try {
            return processEngine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey, processVariables);
        } catch (BusinessErrorException exception) {
            throw exception;
        } catch (Exception ex) {
            throw new BusinessErrorException(BusinessErrors.APP_FLOW_001.getCode(), BusinessErrors.APP_FLOW_001.getDescription() + processDefinitionKey + ", " + businessKey);
        }
    }

    public Map<String, Object> startProcessWithVariablesInReturn(String processDefinitionKey, String businessKey, Map<String, Object> processVariables) throws BusinessErrorException {
        try {
            ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey, processVariables);
            Map<String, Object> returnVariables = new HashMap<>(processEngine.getRuntimeService().getVariables(processInstance.getId()));
            returnVariables.put("ProcessInstanceId", processInstance.getProcessInstanceId());
            return returnVariables;
        } catch (BusinessErrorException exception) {
            throw exception;
        } catch (Exception ex) {
            throw new BusinessErrorException(BusinessErrors.APP_FLOW_001.getCode(), BusinessErrors.APP_FLOW_001.getDescription() + processDefinitionKey + ", " + businessKey);
        }
    }

    public void correlateMessage(String messageName, String businessKey, Map<String, Object> processVariables) throws BusinessErrorException {
        try {
            // Flowable 7 移除了 RuntimeService.correlateMessage：改为按 businessKey + 消息订阅查找执行，再 messageEventReceived
            Execution execution = processEngine.getRuntimeService().createExecutionQuery()
                    .processInstanceBusinessKey(businessKey).messageEventSubscriptionName(messageName).singleResult();
            if (execution != null) {
                processEngine.getRuntimeService().messageEventReceived(messageName, execution.getId(), processVariables);
            }
        } catch (BusinessErrorException exception) {
            throw exception;
        } catch (Exception ex) {
            throw new BusinessErrorException(BusinessErrors.APP_FLOW_002.getCode(), BusinessErrors.APP_FLOW_002.getDescription() + messageName + ", " + businessKey);
        }
    }

    public Map<String, Object> correlateMessageWithVariablesInReturn(String messageName, String businessKey, Map<String, Object> processVariables) throws BusinessErrorException {
        try {
            Execution execution = processEngine.getRuntimeService().createExecutionQuery()
                    .processInstanceBusinessKey(businessKey).messageEventSubscriptionName(messageName).singleResult();
            if (execution != null) {
                processEngine.getRuntimeService().messageEventReceived(messageName, execution.getId(), processVariables);
            }
            ProcessInstance processInstance = processEngine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceBusinessKey(businessKey).singleResult();
            return processInstance == null ? Collections.emptyMap() : new HashMap<>(processEngine.getRuntimeService().getVariables(processInstance.getId()));
        } catch (BusinessErrorException exception) {
            throw exception;
        } catch (Exception ex) {
            throw new BusinessErrorException(BusinessErrors.APP_FLOW_002.getCode(), BusinessErrors.APP_FLOW_002.getDescription() + messageName + ", " + businessKey);
        }
    }
}
