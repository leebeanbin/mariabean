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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisherScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poll.delay:30000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = fetchPending();
        if (pendingEvents.isEmpty()) return;

        for (OutboxEvent event : pendingEvents) {
            try {
                objectMapper.readTree(event.getPayload()); // validate JSON
                String topic = determineTopic(event.getAggregateType(), event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                markPublished(event);
                log.debug("Outbox event {} published to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.warn("Outbox event {} publish failed (retry {}): {}", event.getId(), event.getRetryCount(), e.getMessage());
                markFailed(event);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> fetchPending() {
        return outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);
    }

    @Transactional
    public void markPublished(OutboxEvent event) {
        event.markAsPublished();
        outboxEventRepository.save(event);
    }

    @Transactional
    public void markFailed(OutboxEvent event) {
        event.incrementRetry();
        outboxEventRepository.save(event);
    }

    private String determineTopic(String aggregateType, String eventType) {
        if ("PAYMENT".equals(aggregateType) && "PaymentApprovedEvent".equals(eventType)) {
            return "payment-approved";
        }
        if ("RESERVATION".equals(aggregateType)) {
            return "reservation-events";
        }
        if ("AI_EMBEDDING".equals(aggregateType)) {
            return "ai-embedding-tasks";
        }
        return "domain-events";
    }
}
