package zxf.flowable.eventregistry.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.runtime.Execution;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka 消息监听器：收到 package-received topic 消息后，按 orderId(businessKey) 关联到等待中的 BPMN 流程。
 *
 * <p>这是「MQ → BPMN 关联推进」的最常见模式（Pattern 1）：
 * <pre>
 *   外部系统 → Kafka topic → @KafkaListener → messageEventReceived(按 businessKey) → 流程推进
 * </pre>
 *
 * <p>消息格式（JSON）：{@code {"orderId":"ORDER-001","trackingNumber":"TRK-123"}}
 * <br>orderId 与启动流程时的 businessKey 匹配。
 *
 * <p>对比 Event Registry 声明式：这里用代码做关联（messageEventReceived），
 * Event Registry 则用 inbound channel JSON 配置做声明式自动关联（零代码，见 docs/event-registry-example/）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {
    private final ProcessEngine processEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "package-received", groupId = "flowable-demo")
    public void onPackageReceived(String message) {
        log.info("Kafka message received: {}", message);
        try {
            JsonNode json = objectMapper.readTree(message);
            String orderId = json.path("orderId").asText();
            String trackingNumber = json.path("trackingNumber").asText();

            // 按 businessKey(orderId) 找到等待 packageReceived 消息的执行
            Execution execution = processEngine.getRuntimeService().createExecutionQuery()
                    .processInstanceBusinessKey(orderId)
                    .messageEventSubscriptionName("packageReceived")
                    .singleResult();

            if (execution != null) {
                // 设置消息载荷变量 + 投递消息 → 流程从 waitPackage 推进到 confirm → end
                Map<String, Object> variables = Map.of("trackingNumber", trackingNumber);
                processEngine.getRuntimeService()
                        .messageEventReceived("packageReceived", execution.getId(), variables);
                log.info("Correlated to process, orderId={}, executionId={}", orderId, execution.getId());
            } else {
                log.warn("No waiting process found for orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", message, e);
        }
    }
}
