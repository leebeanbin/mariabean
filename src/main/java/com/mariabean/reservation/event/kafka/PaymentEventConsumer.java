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

/**
 * Kafka Consumer: payment-approved 이벤트를 수신하여 알림 발송 등 비동기 사이드이펙트를 처리합니다.
 * 
 * @RetryableTopic으로 3회 재시도 후 DLQ(Dead Letter Queue)로 이동합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper; // Spring Bean 주입
    private final NotificationService notificationService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = "payment-approved", groupId = "reservation-group", autoStartup = "${spring.kafka.enabled:true}")
    public void handlePaymentApproved(String message) {
        PaymentApprovedEvent event = deserialize(message, PaymentApprovedEvent.class);
        log.info("[Kafka Consumer] Payment approved: paymentId={}, reservationId={}, amount={}",
                event.getPaymentId(), event.getReservationId(), event.getAmount());

        notificationService.sendPaymentConfirmation(
                event.getMemberId(), event.getReservationId(),
                event.getAmount(), event.getProvider());
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[Kafka DLT] 처리 실패 메시지를 DLQ로 이동: {}", message);
        // TODO: DB에 실패 이벤트 저장 or 알림 발송
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
