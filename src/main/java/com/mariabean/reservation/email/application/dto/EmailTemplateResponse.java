package com.mariabean.reservation.email.application.dto;

import com.mariabean.reservation.email.domain.EmailTemplate;

import java.time.LocalDateTime;
import java.util.List;

public record EmailTemplateResponse(
        Long id,
        String name,
        String subject,
        String body,
        List<String> variables,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EmailTemplateResponse from(EmailTemplate t) {
        return new EmailTemplateResponse(
                t.getId(), t.getName(), t.getSubject(), t.getBody(),
                t.getVariables(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
