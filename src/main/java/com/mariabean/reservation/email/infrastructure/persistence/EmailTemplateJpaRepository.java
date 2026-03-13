package com.mariabean.reservation.email.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateJpaRepository extends JpaRepository<EmailTemplateJpaEntity, Long> {
}
