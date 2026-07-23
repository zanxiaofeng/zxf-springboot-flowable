package zxf.flowable.arch.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }

    // Camunda 版监听 PostDeployEvent / PreUndeployEvent；Flowable 无对应事件，这里用应用就绪事件近似。
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("on ApplicationReady (Flowable engine deployed): {}", event.getTimeTaken());
    }
}
