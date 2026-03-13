package com.mariabean.reservation.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KafkaTemplate.class)
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishPaymentApproved(PaymentApprovedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PaymentApprovedEvent.TOPIC, String.valueOf(event.getPaymentId()), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[Kafka] PaymentApproved 전송 실패 paymentId={}: {}", event.getPaymentId(), ex.getMessage());
                        }
                    });
            log.debug("Published PaymentApprovedEvent for paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.warn("[Kafka] PaymentApproved 발행 스킵: {}", e.getMessage());
        }
    }
}
