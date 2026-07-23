package zxf.flowable.saga.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@RequiredArgsConstructor
@Configuration
public class Startup {
    private final ExtSaga extSaga;

    @PostConstruct
    public void startUp() {
        extSaga.deploySaga();
    }
}
