package com.mariabean.reservation.email.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

public record ScheduledEmailRequest(
        @NotNull Long templateId,
        Long recipientMemberId,           // optional — 멤버 ID로 이메일 조회
        @Email @NotBlank String recipientEmail,
        @NotNull LocalDateTime scheduledAt,
        Map<String, String> variables
) {}
