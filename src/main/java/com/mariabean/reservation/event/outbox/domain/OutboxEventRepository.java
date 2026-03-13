package com.mariabean.reservation.event.outbox.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);

    List<OutboxEvent> findOldPendingEvents(OutboxEvent.OutboxStatus status, LocalDateTime before);
}
