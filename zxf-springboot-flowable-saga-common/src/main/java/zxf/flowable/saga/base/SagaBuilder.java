package zxf.flowable.saga.base;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ErrorEventDefinition;
import org.flowable.bpmn.model.EventSubProcess;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Flowable 的 BpmnModel 对象 API 在运行时动态构建并部署 Saga 流程。
 *
 * <p>对应 Camunda 版基于 {@code org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess()} 的流式 API。
 * Flowable 的 bpmn-model 无流式构建器，故手工组装 {@link BpmnModel}（API 经 javap 核实）：
 * <ul>
 *   <li>service task 的 Spring delegate：{@link ServiceTask#setImplementation("${beanName}")} + {@link ImplementationType#IMPLEMENTATION_TYPE_DELEGATEEXPRESSION}
 *   （Flowable 的 {@code flowable:class} 仅反射实例化、无 Spring DI，故用 delegateExpression 按 Bean 名解析）</li>
 *   <li>{@code flowable:failedJobRetryTimeCycle} → {@link Activity#setFailedJobRetryTimeCycleValue(String)}</li>
 *   <li>{@code flowable:asyncBefore/asyncAfter} → {@link FlowNode#setAsynchronous(boolean)} / {@link FlowNode#setAsynchronousLeave(boolean)}</li>
 * </ul>
 *
 * <p><b>补偿实现（关键，已调通）</b>：Camunda 版用「事件子流程内 Error StartEvent → Compensate ThrowEvent」触发 BPMN 补偿。
 * 经多轮历史表/字节码诊断，Flowable 中 BPMN 补偿事件在本场景不可用：
 * <ul>
 *   <li>事件子流程内的 Compensate ThrowEvent 只补偿子流程自身作用域，不补偿父流程任务；</li>
 *   <li>把任务包进子流程再被 boundary error 中断会丢弃补偿注册；</li>
 *   <li>同一任务同时挂 compensation boundary 与 error boundary 会触发 "Parent activity not found"。</li>
 * </ul>
 * 故改为<b>手动补偿</b>（已验证可靠）：
 * <ol>
 *   <li>每个 saga 任务完成时由其适配器设置完成标记变量（VAR_OF_TASK&lt;n&gt;）；{@link #compensationActivity} 记录
 *   「VAR_OF_TASK&lt;n&gt;:undoBean」映射。</li>
 *   <li>{@link #triggerCompensationOnAnyError} 生成一个错误事件子流程（捕获任意 BpmnError），其中 service task 以
 *   method-expression 调用 {@link CompensationDelegate#compensate(String, org.flowable.engine.delegate.DelegateExecution)}，
 *   逆序遍历映射、仅对已完成的任务调用其 undo 适配器 Bean。</li>
 * </ol>
 * 效果与 Camunda 版补偿语义一致：失败任务的已完成前置任务按逆序 undo。
 *
 * <p>Flowable 的错误事件只捕获 {@code org.flowable.engine.delegate.BpmnError}（Camunda 可按异常类名捕获任意 Throwable），
 * 因此补偿型 saga 任务适配器失败时需抛出 BpmnError；重试型 saga（App3/App4）仍抛 RuntimeException 走重试。
 */
@Slf4j
public class SagaBuilder {
    private final String name;
    private final boolean asyncBefore;
    private final boolean asyncAfter;
    private final Process process = new Process();
    private final BpmnModel bpmnModel = new BpmnModel();
    private final List<String> undoChain = new ArrayList<>();
    private FlowNode current;
    private int seq = 0;
    private int taskIndex = 0;

    private SagaBuilder(String name, boolean asyncBefore, boolean asyncAfter) {
        this.name = name;
        this.asyncBefore = asyncBefore;
        this.asyncAfter = asyncAfter;
    }

    public static SagaBuilder newSaga(String name, boolean asyncBefore, boolean asyncAfter) {
        SagaBuilder builder = new SagaBuilder(name, asyncBefore, asyncAfter);
        builder.process.setId(name);
        builder.process.setName(name);
        builder.process.setExecutable(true);
        builder.bpmnModel.addProcess(builder.process);
        StartEvent startEvent = new StartEvent();
        startEvent.setId("Start-" + name);
        builder.process.addFlowElement(startEvent);
        builder.current = startEvent;
        return builder;
    }

    public BpmnModel getModel() {
        log.info("BPMN: {}", new String(new BpmnXMLConverter().convertToXML(bpmnModel)));
        return bpmnModel;
    }

    public SagaBuilder end() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId("End-" + name);
        connect(endEvent);
        return this;
    }

    /**
     * 添加一个立即失败（无重试）的 service task。R1/PT0S：执行 1 次，失败不重试。
     */
    public SagaBuilder activityNoRetry(String activityName, String adapterClass) {
        return activity(activityName, adapterClass, "R1/PT0S");
    }

    /**
     * 添加一个带重试周期的 service task。周期格式 R&lt;count&gt;/&lt;period&gt;，如 R3/PT5S = 最多执行 3 次、间隔 5 秒。
     */
    public SagaBuilder activity(String activityName, String adapterClass, String retryTimeCycle) {
        ServiceTask task = newServiceTask(activityName, adapterClass, retryTimeCycle);
        connect(task);
        taskIndex++;
        return this;
    }

    /**
     * 为最近一个 activity 登记补偿：记录「VAR_OF_TASK&lt;n&gt;:undoBean」映射（n = 当前任务序号），
     * 由 {@link CompensationDelegate} 在失败时逆序调用对应 undo 适配器。
     */
    public SagaBuilder compensationActivity(String activityName, String adapterClass) {
        String undoBean = java.beans.Introspector.decapitalize(adapterClass.substring(adapterClass.lastIndexOf('.') + 1));
        undoChain.add("VAR_OF_TASK" + taskIndex + ":" + undoBean);
        return this;
    }

    /**
     * 失败补偿：错误事件子流程捕获任意 BpmnError → 调用 CompensationDelegate 逆序补偿 → 结束。
     */
    public SagaBuilder triggerCompensationOnAnyError() {
        return triggerCompensationActivityOnAnyError(null, null);
    }

    /**
     * 失败补偿：CompensationDelegate 逆序补偿 → 执行 finally-undo 任务 → 结束。
     * （Undo 顺序：Undo Task N → ... → Undo Task 1 → Finally Undo）
     */
    public SagaBuilder triggerCompensationActivityOnAnyError(String activityName, String adapterClass) {
        EventSubProcess subProcess = new EventSubProcess();
        subProcess.setId("EventSubProcess-ErrorCatched-" + name);
        process.addFlowElement(subProcess);

        // 1. Error start event（不指定 errorRef → 捕获任意 BpmnError）
        StartEvent errorStart = new StartEvent();
        errorStart.setId("ErrorCatched-" + name);
        ErrorEventDefinition errorEventDefinition = new ErrorEventDefinition();
        errorStart.addEventDefinition(errorEventDefinition);
        subProcess.addFlowElement(errorStart);

        // 2. CompensationDelegate（method-expression，传入 undoChain）
        String chain = String.join(",", undoChain);
        ServiceTask compensateTask = new ServiceTask();
        compensateTask.setId("Compensate-" + name);
        compensateTask.setName("Compensate");
        compensateTask.setImplementation("${compensationDelegate.compensate('" + chain + "', execution)}");
        compensateTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION);
        subProcess.addFlowElement(compensateTask);
        connectInside(subProcess, errorStart, compensateTask);

        FlowNode last = compensateTask;

        // 3. （可选）补偿后的 finally-undo 任务
        if (activityName != null && adapterClass != null) {
            ServiceTask finallyUndo = newServiceTask(activityName, adapterClass, null);
            finallyUndo.setId("CompensationActivity-" + activityName.replace(" ", "-"));
            subProcess.addFlowElement(finallyUndo);
            connectInside(subProcess, last, finallyUndo);
            last = finallyUndo;
        }

        EndEvent errorHandled = new EndEvent();
        errorHandled.setId("ErrorHandled-" + name);
        subProcess.addFlowElement(errorHandled);
        connectInside(subProcess, last, errorHandled);

        return this;
    }

    private ServiceTask newServiceTask(String activityName, String adapterClass, String retryTimeCycle) {
        ServiceTask task = new ServiceTask();
        task.setId("Activity-" + activityName.replace(" ", "-"));
        task.setName(activityName);
        // Camunda 版用 camunda:class（Camunda Spring Boot 把 FQN 解析为 Spring Bean，支持构造注入）。
        // Flowable 的 flowable:class 仅反射实例化（无 Spring DI），故用 delegateExpression 按 Bean 名解析。
        String beanName = java.beans.Introspector.decapitalize(adapterClass.substring(adapterClass.lastIndexOf('.') + 1));
        task.setImplementation("${" + beanName + "}");
        task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        task.setAsynchronous(asyncBefore);
        task.setAsynchronousLeave(asyncAfter);
        if (retryTimeCycle != null) {
            task.setFailedJobRetryTimeCycleValue(retryTimeCycle);
        }
        return task;
    }

    private void connect(FlowNode next) {
        process.addFlowElement(next);
        if (current != null) {
            process.addFlowElement(sequenceFlow(current, next));
        }
        current = next;
    }

    private void connectInside(FlowElementsContainer container, FlowNode source, FlowNode target) {
        container.addFlowElement(sequenceFlow(source, target));
    }

    private SequenceFlow sequenceFlow(FlowNode source, FlowNode target) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId("sequenceFlow-" + name + "-" + (seq++));
        flow.setSourceRef(source.getId());
        flow.setTargetRef(target.getId());
        return flow;
    }
}
