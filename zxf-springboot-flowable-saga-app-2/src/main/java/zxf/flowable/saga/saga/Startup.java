package zxf.flowable.saga.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@RequiredArgsConstructor
@Configuration
public class Startup {
    private final App2Saga app2Saga;
    private final App4Saga app4Saga;

    @PostConstruct
    public void startUp() {
        app2Saga.deploySaga();
        app4Saga.deploySaga();
    }
}
