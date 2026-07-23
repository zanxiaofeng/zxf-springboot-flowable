package zxf.flowable.arch.rest.request;

import lombok.Data;

import java.util.Map;

@Data
public class FormRequest {
    private String formId;
    private Map<String, Object> formData;

    public Integer getCode() {
        return (Integer) formData.get("code");
    }
}
