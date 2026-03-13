package com.mariabean.reservation.email.infrastructure.persistence;

import com.mariabean.reservation.email.domain.ScheduledEmail;
import com.mariabean.reservation.email.domain.ScheduledEmailRepository;
import com.mariabean.reservation.email.domain.ScheduledEmailStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduledEmailPersistenceAdapter implements ScheduledEmailRepository {

    private final ScheduledEmailJpaRepository jpaRepository;

    @Override
    public ScheduledEmail save(ScheduledEmail email) {
        ScheduledEmailJpaEntity entity = ScheduledEmailJpaEntity.fromDomain(email);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<ScheduledEmail> findById(Long id) {
        return jpaRepository.findById(id).map(ScheduledEmailJpaEntity::toDomain);
    }

    @Override
    public List<ScheduledEmail> findPendingDue(LocalDateTime now) {
        return jpaRepository.findPendingDue(now)
                .stream()
                .map(ScheduledEmailJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Page<ScheduledEmail> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(ScheduledEmailJpaEntity::toDomain);
    }

    @Override
    public Page<ScheduledEmail> findByStatus(ScheduledEmailStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable).map(ScheduledEmailJpaEntity::toDomain);
    }
}
