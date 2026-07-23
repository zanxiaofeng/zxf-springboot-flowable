package zxf.flowable.arch.app.client.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Slf4j
public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            ClientHttpResponse response = execution.execute(request, body);
            boolean isError = !response.getStatusCode().is2xxSuccessful();

            if (isError || log.isDebugEnabled()) {
                logRequest(request, body, isError ? log::error : log::debug);
                logResponse(response, isError ? log::error : log::debug);
            }

            return response;
        } catch (Exception ex) {
            log.error("Exception when sending request", ex);
            logRequest(request, body, log::error);
            throw ex;
        }
    }

    private void logRequest(HttpRequest request, byte[] body, Consumer<String> logger) {
        logger.accept("=================================================Request begin=================================================");
        logger.accept("URI             : " + request.getURI());
        logger.accept("Methed          : " + request.getMethod());
        logger.accept("Headers         : " + removeSensitiveHeaders(request.getHeaders()));
        logger.accept("Request Body    : " + new String(body, StandardCharsets.UTF_8));
        logger.accept("=================================================Request end=================================================");
    }

    private void logResponse(ClientHttpResponse response, Consumer<String> logger) throws IOException {
        logger.accept("=================================================Response begin=================================================");
        logger.accept("Status code     : " + response.getStatusCode());
        logger.accept("Headers         : " + response.getHeaders());
        logger.accept("Response Body   : " + toString(response));
        logger.accept("=================================================Response end=================================================");
    }

    private String toString(ClientHttpResponse response) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String inputStr;
            StringBuilder builder = new StringBuilder();
            while ((inputStr = bufferedReader.readLine()) != null) {
                builder.append(inputStr);
            }
            return builder.toString();
        }
    }

    private HttpHeaders removeSensitiveHeaders(HttpHeaders originalHttpHeaders) {
        HttpHeaders clearHttpHeaders = new HttpHeaders();
        clearHttpHeaders.putAll(originalHttpHeaders);
        clearHttpHeaders.remove("Token");
        clearHttpHeaders.remove("Authorization");
        return clearHttpHeaders;
    }
}