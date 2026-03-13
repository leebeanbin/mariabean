package com.mariabean.reservation.event.outbox.infrastructure.persistence;

import com.mariabean.reservation.event.outbox.domain.OutboxEvent;
import com.mariabean.reservation.event.outbox.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    public List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status) {
        return jpaRepository.findTop100ByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    public List<OutboxEvent> findOldPendingEvents(OutboxEvent.OutboxStatus status, LocalDateTime before) {
        return jpaRepository.findOldPendingEvents(status, before);
    }
}
