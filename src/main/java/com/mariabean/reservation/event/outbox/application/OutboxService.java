package com.mariabean.reservation.event.outbox.application;

import com.mariabean.reservation.event.outbox.domain.OutboxEvent;
import com.mariabean.reservation.event.outbox.domain.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 비즈니스 로직 트랜잭션 내에서 호출되어야 합니다.
     * 자체적인 save 작업을 통해 DB에 PENDING 상태로 이벤트를 기록합니다.
     */
    public <T> void saveEvent(String aggregateType, String aggregateId, String eventType, T eventPayload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: type={}, id={}", eventType, aggregateId);
        } catch (Exception e) {
            log.error("Failed to serialize and save outbox event for aggregate: {}", aggregateId, e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}
