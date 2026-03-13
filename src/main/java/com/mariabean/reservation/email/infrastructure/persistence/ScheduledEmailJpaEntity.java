package com.mariabean.reservation.email.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.email.domain.ScheduledEmail;
import com.mariabean.reservation.email.domain.ScheduledEmailStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "scheduled_emails", indexes = {
        @Index(name = "idx_scheduled_emails_status_scheduled_at", columnList = "status, scheduledAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ScheduledEmailJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    private Long recipientMemberId;

    @Column(nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(columnDefinition = "TEXT")
    private String variablesJson; // JSON map

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledEmailStatus status;

    @Column(length = 500)
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ScheduledEmail toDomain() {
        Map<String, String> vars = parseVariables();
        return new ScheduledEmail(id, templateId, recipientMemberId, recipientEmail,
                scheduledAt, vars, status, errorMessage, createdAt);
    }

    public static ScheduledEmailJpaEntity fromDomain(ScheduledEmail email) {
        String json = serializeVariables(email.getVariables());
        return ScheduledEmailJpaEntity.builder()
                .id(email.getId())
                .templateId(email.getTemplateId())
                .recipientMemberId(email.getRecipientMemberId())
                .recipientEmail(email.getRecipientEmail())
                .scheduledAt(email.getScheduledAt())
                .variablesJson(json)
                .status(email.getStatus() != null ? email.getStatus() : ScheduledEmailStatus.PENDING)
                .errorMessage(email.getErrorMessage())
                .build();
    }

    public void markSent() {
        this.status = ScheduledEmailStatus.SENT;
    }

    public void markFailed(String reason) {
        this.status = ScheduledEmailStatus.FAILED;
        this.errorMessage = reason;
    }

    private Map<String, String> parseVariables() {
        if (variablesJson == null || variablesJson.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(variablesJson, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static String serializeVariables(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(vars);
        } catch (Exception e) {
            return "{}";
        }
    }
}
