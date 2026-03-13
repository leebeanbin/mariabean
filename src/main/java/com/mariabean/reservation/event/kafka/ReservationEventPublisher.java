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
public class ReservationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishReservationCreated(ReservationEvent event) {
        publish(ReservationEvent.TOPIC, event);
    }

    public void publishReservationCancelled(ReservationEvent event) {
        publish(ReservationEvent.TOPIC, event);
    }

    private void publish(String topic, ReservationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(event.getReservationId()), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[Kafka] {} 전송 실패 reservationId={}: {}", event.getEventType(), event.getReservationId(), ex.getMessage());
                        }
                    });
            log.debug("[Kafka] Published {} for reservationId={}", event.getEventType(), event.getReservationId());
        } catch (Exception e) {
            log.warn("[Kafka] {} 발행 스킵: {}", event.getEventType(), e.getMessage());
        }
    }
}
