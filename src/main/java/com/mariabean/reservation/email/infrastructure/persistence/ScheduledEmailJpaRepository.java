package com.mariabean.reservation.email.infrastructure.persistence;

import com.mariabean.reservation.email.domain.ScheduledEmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledEmailJpaRepository extends JpaRepository<ScheduledEmailJpaEntity, Long> {

    @Query("SELECT e FROM ScheduledEmailJpaEntity e WHERE e.status = 'PENDING' AND e.scheduledAt <= :now ORDER BY e.scheduledAt ASC")
    List<ScheduledEmailJpaEntity> findPendingDue(LocalDateTime now);

    Page<ScheduledEmailJpaEntity> findByStatus(ScheduledEmailStatus status, Pageable pageable);
}
