package com.mariabean.reservation.email.domain;

import java.time.LocalDateTime;
import java.util.Map;

public class ScheduledEmail {
    private Long id;
    private Long templateId;
    private Long recipientMemberId;   // optional
    private String recipientEmail;
    private LocalDateTime scheduledAt;
    private Map<String, String> variables;
    private ScheduledEmailStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public ScheduledEmail() {}

    public ScheduledEmail(Long id, Long templateId, Long recipientMemberId, String recipientEmail,
                          LocalDateTime scheduledAt, Map<String, String> variables,
                          ScheduledEmailStatus status, String errorMessage, LocalDateTime createdAt) {
        this.id = id;
        this.templateId = templateId;
        this.recipientMemberId = recipientMemberId;
        this.recipientEmail = recipientEmail;
        this.scheduledAt = scheduledAt;
        this.variables = variables;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getTemplateId() { return templateId; }
    public Long getRecipientMemberId() { return recipientMemberId; }
    public String getRecipientEmail() { return recipientEmail; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public Map<String, String> getVariables() { return variables; }
    public ScheduledEmailStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void markSent() { this.status = ScheduledEmailStatus.SENT; }
    public void markFailed(String reason) {
        this.status = ScheduledEmailStatus.FAILED;
        this.errorMessage = reason;
    }
}
