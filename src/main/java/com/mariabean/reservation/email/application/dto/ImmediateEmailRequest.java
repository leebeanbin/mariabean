package com.mariabean.reservation.email.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ImmediateEmailRequest(
        @NotNull Long templateId,
        @Email @NotBlank String recipientEmail,
        Map<String, String> variables
) {}
