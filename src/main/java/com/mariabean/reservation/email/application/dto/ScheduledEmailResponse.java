package com.mariabean.reservation.email.application.dto;

import com.mariabean.reservation.email.domain.ScheduledEmail;
import com.mariabean.reservation.email.domain.ScheduledEmailStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ScheduledEmailResponse(
        Long id,
        Long templateId,
        String recipientEmail,
        LocalDateTime scheduledAt,
        Map<String, String> variables,
        ScheduledEmailStatus status,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static ScheduledEmailResponse from(ScheduledEmail e) {
        return new ScheduledEmailResponse(
                e.getId(), e.getTemplateId(), e.getRecipientEmail(),
                e.getScheduledAt(), e.getVariables(), e.getStatus(),
                e.getErrorMessage(), e.getCreatedAt()
        );
    }
}
