package zxf.flowable.saga.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.base.SagaBuilder;
import zxf.flowable.saga.service.FlowableService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ExtSaga（对应 Camunda 版的 ExtSaga）。
 *
 * <p>差异：原项目用 Camunda External Task（camunda:type="external"）+ LocalExternalTaskWorker 轮询。
 * Flowable 无外部任务概念，这里改为普通异步 service task，由 Flowable async job executor 负责异步获取与执行；
 * LocalExternalTaskWorker 已移除，工作逻辑下沉到 {@code zxf.flowable.saga.task.ext.ExtTask*Delegate}。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ExtSaga {
    private final String sagaName = "ext-v1";
    private final ProcessEngine processEngine;
    private final FlowableService flowableService;

    public void deploySaga() {
        log.info("{} deploySaga start", this.sagaName);

        try {
            if (flowableService.sagaRedeploy() || !isSagaDeployed()) {
                BpmnModel bpmnModel = buildSaga();
                Deployment deployment = processEngine.getRepositoryService().createDeployment()
                        .addBpmnModel(this.sagaName + ".bpmn", bpmnModel).deploy();
                log.info("{} saga deployment is done. (DeploymentId={})", this.sagaName, deployment.getId());
                return;
            }

            if (flowableService.registerDeployment()) {
                ProcessDefinition processDefinition = processEngine.getRepositoryService()
                        .createProcessDefinitionQuery().processDefinitionKey(this.sagaName).latestVersion().singleResult();
                flowableService.registerDeploymentForJobExecutor(processDefinition.getDeploymentId());
                log.info("{} saga had been deployed, register job executor to the latest deployment. (DeploymentId={})", this.sagaName, processDefinition.getDeploymentId());
            }
        } catch (Exception ex) {
            log.error("Exception when deploy {} saga or register job executor", this.sagaName, ex);
        }

        log.info("{} deploySaga end", this.sagaName);
    }

    public String trigger(Integer times, Integer count, Integer start) {
        start = Optional.ofNullable(start).orElse(1000);
        log.info("{} trigger start, {}, {}::{}~{}", this.sagaName, getPrefix(), times, start, count);
        for (int i = start; i < start + count; i++) {
            createInstance(times, i);
        }
        log.info("{} trigger end, {}, {}::{}~{}", this.sagaName, getPrefix(), times, start, count);
        return String.format("%s#%d-%d~%d", getPrefix(), times, start, count);
    }

    public void createInstance(Integer times, int number) {
        String taskId = getPrefix() + "#" + times + "-" + number;
        Map<String, Object> someVariables = new HashMap<>();
        someVariables.put("task-id", taskId);
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(this.sagaName, someVariables);
        log.info("{} instance, {}, {}", this.sagaName, taskId, flowableService.instanceInfo(processInstance));
    }

    public String getPrefix() {
        return "ext@" + flowableService.appName();
    }

    private Boolean isSagaDeployed() {
        Boolean hadBeenDeployed = processEngine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey(this.sagaName).count() > 0;
        log.info("{} isSagaDeployed, {}", this.sagaName, hadBeenDeployed);
        return hadBeenDeployed;
    }

    private BpmnModel buildSaga() {
        // 改为普通异步 service task（原 Camunda 版为 externalActivity），由 Flowable job executor 执行
        return SagaBuilder.newSaga(this.sagaName, flowableService.asyncBefore(), flowableService.asyncAfter())
                .activityNoRetry("Ext-Task 1", "zxf.flowable.saga.task.ext.ExtTask1Delegate")
                .activityNoRetry("Ext-Task 2", "zxf.flowable.saga.task.ext.ExtTask2Delegate")
                .activityNoRetry("Ext-Task 3", "zxf.flowable.saga.task.ext.ExtTask3Delegate")
                .end()
                .getModel();
    }
}
