package com.mariabean.reservation.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = ReservationEvent.TOPIC, groupId = "reservation-event-group", autoStartup = "${spring.kafka.enabled:true}")
    public void handleReservationEvent(String message) {
        ReservationEvent event = deserialize(message, ReservationEvent.class);
        if (!ReservationEvent.EVENT_CANCELLED.equalsIgnoreCase(event.getEventType())) {
            log.debug("[Kafka Consumer] skip reservation event type={}", event.getEventType());
            return;
        }

        notificationService.sendReservationCancellation(event.getMemberId(), event.getReservationId());
    }

    @DltHandler
    public void handleDlt(String message) {
        // FAILED 상태 이벤트는 DB(outbox_events)에 남아 운영자가 재처리할 수 있도록 보존합니다.
        log.error("[Kafka DLT] reservation event failed after retries: {}", message);
    }

    private <T> T deserialize(String message, Class<T> clazz) {
        try {
            return objectMapper.readValue(message, clazz);
        } catch (Exception e) {
            log.error("[Kafka Consumer] Failed to deserialize message: {}", message, e);
            throw new RuntimeException("Kafka deserialization failed", e);
        }
    }
}
