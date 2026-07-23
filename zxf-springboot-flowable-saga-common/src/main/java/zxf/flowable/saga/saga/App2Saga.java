package zxf.flowable.saga.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.stereotype.Component;
import zxf.flowable.saga.base.SagaBuilder;
import zxf.flowable.saga.service.FlowableService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class App2Saga {
    private final String sagaName = "app2-v16";
    private final ProcessEngine processEngine;
    private final FlowableService flowableService;

    public void deploySaga() {
        log.info("{} deploySaga start", this.sagaName);

        try {
            if (flowableService.sagaRedeploy() || !isSagaDeployed()) {
                BpmnModel bpmnModelInstance = buildSaga();
                Deployment deployment = processEngine.getRepositoryService().createDeployment()
                        .addBpmnModel(this.sagaName + ".bpmn", bpmnModelInstance).deploy();
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
        start = Optional.ofNullable(start).orElse(2000);
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
        //This method will always create instance base on the latest version.
        //If you use business key, that business key must be unique.
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(this.sagaName, someVariables);
        log.info("{} instance, {}, {}", this.sagaName, taskId, flowableService.instanceInfo(processInstance));
    }

    public String getPrefix() {
        return "app2@" + flowableService.appName();
    }

    private Boolean isSagaDeployed() {
        Boolean hadBeenDeployed = processEngine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey(this.sagaName).count() > 0;
        log.info("{} isSagaDeployed, {}", this.sagaName, hadBeenDeployed);
        return hadBeenDeployed;
    }

    private BpmnModel buildSaga() {
        // 异步任务：失败时抛 BpmnError，由错误事件子流程捕获并调用 CompensationDelegate 手动逆序补偿。
        SagaBuilder sagaBuilder = SagaBuilder.newSaga(this.sagaName, flowableService.asyncBefore(), flowableService.asyncAfter())
                .activityNoRetry("App2-Task 1", "zxf.flowable.saga.task.app2.App2Task1Adapter")
                .compensationActivity("App2-Undo Task 1", "zxf.flowable.saga.task.app2.App2Task1UndoAdapter")
                .activityNoRetry("App2-Task 2", "zxf.flowable.saga.task.app2.App2Task2Adapter")
                .compensationActivity("App2-Undo Task 2", "zxf.flowable.saga.task.app2.App2Task2UndoAdapter")
                .activityNoRetry("App2-Task 3", "zxf.flowable.saga.task.app2.App2Task3Adapter")
                .end()
                .triggerCompensationActivityOnAnyError("App2-Finally Undo", "zxf.flowable.saga.task.app2.App2TaskEndUndoAdapter");
        //Undo flow: Undo Task 2 --> Undo Task 1 --> Finally Undo
        return sagaBuilder.getModel();
    }
}
