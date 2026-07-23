package zxf.flowable.saga.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@RequiredArgsConstructor
@Configuration
public class Startup {
    private final App1Saga app1Saga;
    private final App3Saga app3Saga;

    @PostConstruct
    public void startUp() {
        app1Saga.deploySaga();
        app3Saga.deploySaga();
    }
}
