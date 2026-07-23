package zxf.flowable.arch.app.delegate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;
import zxf.flowable.arch.app.service.FlowableService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求体序列化 delegate（对应 Camunda 版同名类）。
 *
 * <p>差异：Camunda 版用 inputOutput 构造 Map（formId/formData）并指定 variableIn/variableOut；
 * Flowable 改为 method-expression {@code ${serializeDelegate.serialize('op', execution)}} + 操作注册表。
 * 每个 operation 构造 {formId: businessKey, formData: <变量>} 并序列化为指定输出变量（JSON 字符串）。
 */
@Slf4j
@Service
public class SerializeDelegate {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FlowableService flowableService;

    public SerializeDelegate(FlowableService flowableService) {
        this.flowableService = flowableService;
    }

    public void serialize(String operation, DelegateExecution execution) {
        SerializeConfig config = CONFIGS.get(operation);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("formId", execution.getProcessInstanceBusinessKey());
        form.put("formData", execution.getVariable(config.formDataVar()));
        try {
            execution.setVariable(config.outVar(), OBJECT_MAPPER.writeValueAsString(form));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        log.info("Serialize, {}", flowableService.executionInfoForService(execution, false));
    }

    private record SerializeConfig(String formDataVar, String outVar) {
    }

    private static final Map<String, SerializeConfig> CONFIGS = Map.of(
            "createFormRequest", new SerializeConfig("requestBody", "createFormRequestBody"),
            "updateFormRequest", new SerializeConfig("messageBody", "updateFormRequestBody"),
            "deleteFormRequest", new SerializeConfig("messageBody", "deleteFormRequestBody"),
            "deleteExpiredFormRequest", new SerializeConfig("requestBody", "deleteFormRequestBody"));
}
