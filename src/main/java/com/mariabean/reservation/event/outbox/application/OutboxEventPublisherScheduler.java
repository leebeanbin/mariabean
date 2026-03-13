package com.mariabean.reservation.event.outbox.application;

import com.mariabean.reservation.event.outbox.domain.OutboxEvent;
import com.mariabean.reservation.event.outbox.domain.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisherScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poll.delay:30000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) return;

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = determineTopic(event.getAggregateType(), event.getEventType());

                objectMapper.readTree(event.getPayload());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);

                event.markAsPublished();
                log.debug("Outbox event {} published to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.warn("Outbox event {} publish failed (will retry): {}", event.getId(), e.getMessage());
                event.markAsFailed();
            }
        }
    }

    private String determineTopic(String aggregateType, String eventType) {
        if ("PAYMENT".equals(aggregateType) && "PaymentApprovedEvent".equals(eventType)) {
            return "payment-approved";
        }
        if ("RESERVATION".equals(aggregateType)) {
            return "reservation-events";
        }
        return "domain-events";
    }
}
