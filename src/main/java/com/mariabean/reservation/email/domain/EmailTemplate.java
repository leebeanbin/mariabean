package com.mariabean.reservation.email.domain;

import java.time.LocalDateTime;
import java.util.List;

public class EmailTemplate {
    private Long id;
    private String name;
    private String subject;
    private String body; // HTML, supports {{variableName}} placeholders
    private List<String> variables; // e.g. ["name", "reservationId", "date"]
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EmailTemplate() {}

    public EmailTemplate(Long id, String name, String subject, String body,
                         List<String> variables, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.variables = variables;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public List<String> getVariables() { return variables; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void update(String name, String subject, String body, List<String> variables) {
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.variables = variables;
        this.updatedAt = LocalDateTime.now();
    }
}
