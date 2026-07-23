package zxf.flowable.arch.app.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum BusinessErrors {
    APP_DOWNSTREAM_001("APP_DOWNSTREAM-001", "Downstream error with exception: "),
    APP_DOWNSTREAM_002("APP_DOWNSTREAM-002", "Downstream error with http code: "),
    APP_DOWNSTREAM_003("APP_DOWNSTREAM-003", "Downstream error with http code and return code: "),
    APP_FLOW_001("APP_FLOW-001", "Can not start process: "),
    APP_FLOW_002("APP_FLOW-002", "Can not correlate message: ");

    private final String code;
    private final String description;
}


