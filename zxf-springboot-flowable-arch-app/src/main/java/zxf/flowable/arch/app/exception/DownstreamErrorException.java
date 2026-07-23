package zxf.flowable.arch.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.HashMap;
import java.util.Map;

@Getter
public class DownstreamErrorException extends BusinessErrorException {
    private Integer downstreamStatusCode;
    private String downstreamReturnCode;

    private DownstreamErrorException(String businessErrorCode, String message) {
        super(businessErrorCode, message);
    }

    private DownstreamErrorException(String businessErrorCode, String message, Throwable cause) {
        super(businessErrorCode, message, cause);
    }

    public Map<String, Object> response() {
        Map<String, Object> downstream = new HashMap<>();
        downstream.put("statusCode", this.downstreamStatusCode);
        downstream.put("returnCode", this.downstreamReturnCode);

        Map<String, Object> response = super.response();
        response.put("downstream", downstream);
        return response;
    }

    public static DownstreamErrorException downstreamErrorWithException(Throwable cause) {
        return new DownstreamErrorException(BusinessErrors.APP_DOWNSTREAM_001.getCode(), BusinessErrors.APP_DOWNSTREAM_001.getDescription(), cause);
    }

    public static DownstreamErrorException downstreamErrorWithHttpStatus(HttpStatusCode httpStatusCode) {
        DownstreamErrorException errorException = new DownstreamErrorException(BusinessErrors.APP_DOWNSTREAM_002.getCode(), BusinessErrors.APP_DOWNSTREAM_002.getDescription());
        errorException.downstreamStatusCode = httpStatusCode.value();
        return errorException;
    }

    public static DownstreamErrorException downstreamErrorWithHttpStatusAndReturnCode(HttpStatusCode httpStatusCode, String returnCode) {
        DownstreamErrorException errorException = new DownstreamErrorException(BusinessErrors.APP_DOWNSTREAM_003.getCode(), BusinessErrors.APP_DOWNSTREAM_003.getDescription());
        errorException.downstreamStatusCode = httpStatusCode.value();
        errorException.downstreamReturnCode = returnCode;
        return errorException;
    }
}
