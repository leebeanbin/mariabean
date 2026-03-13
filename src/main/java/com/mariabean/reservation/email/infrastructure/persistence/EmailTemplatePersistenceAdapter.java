package com.mariabean.reservation.email.infrastructure.persistence;

import com.mariabean.reservation.email.domain.EmailTemplate;
import com.mariabean.reservation.email.domain.EmailTemplateRepository;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailTemplatePersistenceAdapter implements EmailTemplateRepository {

    private final EmailTemplateJpaRepository jpaRepository;

    @Override
    public EmailTemplate save(EmailTemplate template) {
        EmailTemplateJpaEntity entity = EmailTemplateJpaEntity.fromDomain(template);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<EmailTemplate> findById(Long id) {
        return jpaRepository.findById(id).map(EmailTemplateJpaEntity::toDomain);
    }

    @Override
    public Page<EmailTemplate> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(EmailTemplateJpaEntity::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        if (!jpaRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        jpaRepository.deleteById(id);
    }
}
