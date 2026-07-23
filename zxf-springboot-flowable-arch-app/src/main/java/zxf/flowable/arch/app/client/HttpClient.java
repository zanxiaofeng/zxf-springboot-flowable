package zxf.flowable.arch.app.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import zxf.flowable.arch.app.client.http.RestTemplateFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;

@Service
public class HttpClient {
    public static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REFERENCE = new ParameterizedTypeReference<>() {
        @Override
        public Type getType() {
            return super.getType();
        }
    };

    public ResponseEntity<Map<String, Object>> request(String method, String url, String body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setAll(headers);
        RequestEntity<String> requestEntity = new RequestEntity<>(body, httpHeaders, HttpMethod.valueOf(method), URI.create(url));
        return RestTemplateFactory.basicRestTemplate(false).exchange(requestEntity, MAP_PARAMETERIZED_TYPE_REFERENCE);
    }
}
