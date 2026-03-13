package com.mariabean.reservation.event.outbox.infrastructure.persistence;

import com.mariabean.reservation.event.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.createdAt <= :before")
    List<OutboxEvent> findOldPendingEvents(@Param("status") OutboxEvent.OutboxStatus status,
            @Param("before") LocalDateTime before);
}
