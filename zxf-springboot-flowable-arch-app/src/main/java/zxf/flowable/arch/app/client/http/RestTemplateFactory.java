package zxf.flowable.arch.app.client.http;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

public class RestTemplateFactory {
    public static RestTemplate basicRestTemplate(Boolean exceptionable) {
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
        if (!exceptionable) {
            restTemplate.setErrorHandler(new MyResponseErrorHandler());
        }
        restTemplate.getInterceptors().add(new LoggingRequestInterceptor());
        return restTemplate;
    }

    public static RestTemplate restTemplateWithBasicAuth(Boolean exceptionable, String username, String passwd) {
        RestTemplate basicRestTemplate = basicRestTemplate(exceptionable);
        basicRestTemplate.getInterceptors().add(0, new BasicAuthenticationInterceptor(username, passwd));
        return basicRestTemplate;
    }

    public static RestTemplate restTemplateWithTokenAuth(Boolean exceptionable, String token) {
        RestTemplate basicRestTemplate = basicRestTemplate(exceptionable);
        basicRestTemplate.getInterceptors().add(0, new SetHeaderRequestInterceptor("X-Token", token));
        return basicRestTemplate;
    }

    private static ClientHttpRequestFactory clientHttpRequestFactory() {
        return new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
    }
}
