package zxf.flowable.arch.app.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import zxf.flowable.arch.app.client.HttpClient;
import zxf.flowable.arch.app.client.http.response.ResponseHandler;
import zxf.flowable.arch.app.exception.BusinessErrorException;
import zxf.flowable.arch.app.exception.DownstreamErrorException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 HTTP 请求 delegate（对应 Camunda 版同名类）。
 *
 * <p>差异：Camunda 版通过 {@code camunda:inputOutput} 为每个 service task 注入 method/url/body/headers/
 * responseHandler/responseHandleSetting 局部变量；Flowable 无 inputOutput，故改为
 * <b>method-expression + 操作注册表</b>：BPMN 中写 {@code flowable:expression="${httpRequestDelegate.request('op', execution)}"}，
 * 本类按 operation 从 {@link #CONFIGS} 取配置后执行（复用 HttpClient + ResponseHandler）。
 *
 * <p>body 模板支持 {@code ${var}} 占位：整体引用（{@code ${createFormRequestBody}}）取变量值；
 * 内嵌引用（{@code {"task": ${paymentOrderCode}}}）做字符串替换。
 */
@Slf4j
@Service
public class HttpRequestDelegate {
    private static final Pattern WHOLE_VAR = Pattern.compile("^\\$\\{(\\w+)}$");
    private static final Pattern EMBEDDED_VAR = Pattern.compile("\\$\\{(\\w+)}");

    private final HttpClient httpClient;
    private final BeanFactory beanFactory;

    public HttpRequestDelegate(HttpClient httpClient, BeanFactory beanFactory) {
        this.httpClient = httpClient;
        this.beanFactory = beanFactory;
    }

    /** 由 BPMN 的 flowable:expression 调用，如 ${httpRequestDelegate.request('createForm', execution)} */
    public void request(String operation, DelegateExecution execution) {
        Config config = CONFIGS.get(operation);
        try {
            String body = resolveBody(config.bodyTemplate(), execution);
            ResponseEntity<Map<String, Object>> response = httpClient.request(config.method(), config.url(), body, config.headers());

            Map<String, String> setting = new HashMap<>();
            setting.put("Downstream-Non200-Throw", String.valueOf(config.throwNon200()));
            setting.put("Downstream-ErrorCode-Throw", String.valueOf(config.throwError()));
            setting.put("Downstream-Response-Variable", config.responseVar());
            setting.put("Downstream-ReturnCode-Variable", config.returnCodeVar());
            ResponseHandler responseHandler = beanFactory.getBean(config.responseHandler(), ResponseHandler.class);
            responseHandler.handle(execution, response, setting);
        } catch (BusinessErrorException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Exception when sending http request, operation: {}", operation, ex);
            throw DownstreamErrorException.downstreamErrorWithException(ex);
        }
    }

    private String resolveBody(String bodyTemplate, DelegateExecution execution) {
        if (bodyTemplate == null) {
            return null;
        }
        Matcher whole = WHOLE_VAR.matcher(bodyTemplate);
        if (whole.matches()) {
            return String.valueOf(execution.getVariable(whole.group(1)));
        }
        Matcher embedded = EMBEDDED_VAR.matcher(bodyTemplate);
        StringBuilder result = new StringBuilder();
        while (embedded.find()) {
            embedded.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(execution.getVariable(embedded.group(1)))));
        }
        embedded.appendTail(result);
        return result.toString();
    }

    private record Config(String method, String url, String bodyTemplate, Map<String, String> headers,
                          String responseHandler, String responseVar, String returnCodeVar,
                          boolean throwNon200, boolean throwError) {
    }

    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    private static final Map<String, Config> CONFIGS = new HashMap<>();

    static {
        // ---- FormProcess ----
        CONFIGS.put("createForm", new Config("POST", "http://localhost:8191/form/create", "${createFormRequestBody}",
                JSON_HEADERS, "commonResponseHandler", "createFormResponse", "createFormReturnCode", true, false));
        CONFIGS.put("updateForm", new Config("POST", "http://localhost:8191/form/update", "${updateFormRequestBody}",
                JSON_HEADERS, "commonResponseHandler", "createFormResponse", "createFormReturnCode", true, false));
        CONFIGS.put("deleteForm", new Config("POST", "http://localhost:8191/form/delete", "${deleteFormRequestBody}",
                JSON_HEADERS, "commonResponseHandler", "deleteFormResponse", "deleteFormReturnCode", true, false));
        // ---- PaymentProcess ----
        CONFIGS.put("createOrder", new Config("POST", "http://localhost:8191/task/a", "{\"task\": 200}",
                JSON_HEADERS, "commonResponseHandler", "createOrderResponse", "createOrderReturnCode", true, true));
        CONFIGS.put("paymentOrder", new Config("POST", "http://localhost:8191/task/a", "{\"task\": ${paymentOrderCode}}",
                JSON_HEADERS, "commonResponseHandler", "paymentOrderResponse", "paymentOrderReturnCode", false, false));
        CONFIGS.put("shippingRequest", new Config("POST", "http://localhost:8191/task/b", "{\"task\": ${shippingRequestCode}}",
                JSON_HEADERS, "commonResponseHandler", "shippingRequestResponse", "shippingRequestReturnCode", false, false));
        CONFIGS.put("shippingOrder", new Config("POST", "http://localhost:8191/task/c", "{\"task\": ${shippingOrderCode}}",
                JSON_HEADERS, "commonResponseHandler", "shippingOrderResponse", "shippingOrderReturnCode", false, false));
        // ---- PaymentProcess(Activity_Notification) / NotificationSubProcess ----
        CONFIGS.put("notification", new Config("POST", "http://localhost:8191/task/c", "{\"task\": 200}",
                JSON_HEADERS, "commonResponseHandler", "notificationResponse", "notificationReturnCode", false, false));
    }
}
