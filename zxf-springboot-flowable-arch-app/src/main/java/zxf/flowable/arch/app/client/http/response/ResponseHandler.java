package zxf.flowable.arch.app.client.http.response;

import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.http.ResponseEntity;
import zxf.flowable.arch.app.exception.BusinessErrorException;
import zxf.flowable.arch.app.exception.DownstreamErrorException;

import java.util.Map;

public interface ResponseHandler {
    void handle(DelegateExecution execution, ResponseEntity<Map<String, Object>> response, Map<String, String> handleSetting) throws BusinessErrorException;
}
