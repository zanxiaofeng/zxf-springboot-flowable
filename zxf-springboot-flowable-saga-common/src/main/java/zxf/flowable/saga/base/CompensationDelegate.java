package zxf.flowable.saga.base;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 通用补偿 delegate（补偿型 saga 失败时由错误事件子流程调用）。
 *
 * <p><b>背景</b>：Flowable 的 BPMN 补偿事件（compensate throw / boundary compensation）在本项目的场景下存在
 * 作用域与异步兼容性问题（事件子流程内的 compensate throw 不补偿父作用域；boundary error 与 compensation boundary
 * 同挂一个异步任务会触发 "Parent activity not found"）。故改为<b>手动补偿</b>：错误事件子流程捕获 BpmnError 后，
 * 经 method-expression 调用本类 {@link #compensate(String, DelegateExecution)}，按完成标记逆序调用各 undo 适配器。
 *
 * <p>chain 格式（由 {@link SagaBuilder} 按 saga 任务顺序生成，正序）：
 * {@code VAR_OF_TASK2:undoBean2,VAR_OF_TASK1:undoBean1}。本方法逆序遍历，仅当对应完成标记变量已设置时才调用 undo。
 * （与 Camunda 版补偿语义一致：失败任务的已完成前置任务按逆序 undo。）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationDelegate implements JavaDelegate {
    private final ApplicationContext applicationContext;

    /** 由 BPMN method-expression 调用，如 ${compensationDelegate.compensate('VAR_OF_TASK2:b2,VAR_OF_TASK1:b1', execution)} */
    public void compensate(String chain, DelegateExecution execution) {
        log.info("compensate start, chain={}, execution={}", chain, execution.getId());
        String[] entries = chain.split(",");
        // 逆序：最后完成的先 undo
        for (int i = entries.length - 1; i >= 0; i--) {
            String entry = entries[i].trim();
            int sep = entry.indexOf(':');
            String var = entry.substring(0, sep);
            String undoBean = entry.substring(sep + 1);
            if (execution.getVariable(var) != null) {
                log.info("compensate: {} completed, invoking undo bean {}", var, undoBean);
                applicationContext.getBean(undoBean, JavaDelegate.class).execute(execution);
            } else {
                log.info("compensate: {} not completed, skip undo bean {}", var, undoBean);
            }
        }
        log.info("compensate end, execution={}", execution.getId());
    }

    @Override
    public void execute(DelegateExecution execution) {
        // 占位：实际通过 compensate(chain, execution) method-expression 调用
    }
}
