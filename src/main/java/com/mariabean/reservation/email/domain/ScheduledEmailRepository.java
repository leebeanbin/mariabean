package com.mariabean.reservation.email.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledEmailRepository {
    ScheduledEmail save(ScheduledEmail email);
    Optional<ScheduledEmail> findById(Long id);
    List<ScheduledEmail> findPendingDue(LocalDateTime now);
    Page<ScheduledEmail> findAll(Pageable pageable);
    Page<ScheduledEmail> findByStatus(ScheduledEmailStatus status, Pageable pageable);
}
