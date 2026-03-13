package com.mariabean.reservation.email.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmailTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank String body,
        List<String> variables
) {}
