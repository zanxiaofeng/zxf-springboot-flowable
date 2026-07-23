package zxf.flowable.arch.app.client.http.response;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import zxf.flowable.arch.app.exception.BusinessErrorException;
import zxf.flowable.arch.app.exception.DownstreamErrorException;
import zxf.flowable.arch.app.exception.BusinessErrors;

import java.util.Map;

@Slf4j
@Service
public class CommonResponseHandler implements ResponseHandler {
    @Override
    public void handle(DelegateExecution execution, ResponseEntity<Map<String, Object>> response, Map<String, String> handleSetting) throws BusinessErrorException {
        if (Boolean.parseBoolean(handleSetting.getOrDefault("Downstream-Non200-Throw", "false")) && !response.getStatusCode().is2xxSuccessful()) {
            throw DownstreamErrorException.downstreamErrorWithHttpStatus(response.getStatusCode());
        }

        String downstreamReturnCode = getDownstreamReturnCode(response);
        if (Boolean.parseBoolean(handleSetting.getOrDefault("Downstream-ErrorCode-Throw", "false")) && !"200".equals(downstreamReturnCode)) {
            throw DownstreamErrorException.downstreamErrorWithHttpStatusAndReturnCode(response.getStatusCode(), downstreamReturnCode);
        }

        if (handleSetting.get("Downstream-Response-Variable") != null) {
            execution.setVariable((String) handleSetting.get("Downstream-Response-Variable"), response.getBody());
        }

        if (handleSetting.get("Downstream-ReturnCode-Variable") != null) {
            execution.setVariable((String) handleSetting.get("Downstream-ReturnCode-Variable"), downstreamReturnCode);
        }
    }

    private String getDownstreamReturnCode(ResponseEntity<Map<String, Object>> response) {
        return (String) response.getBody().getOrDefault("code", "");
    }
}
