package com.mariabean.reservation.email.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EmailTemplateRepository {
    EmailTemplate save(EmailTemplate template);
    Optional<EmailTemplate> findById(Long id);
    Page<EmailTemplate> findAll(Pageable pageable);
    void deleteById(Long id);
}
