package com.mariabean.reservation.email.infrastructure.persistence;

import com.mariabean.reservation.email.domain.EmailTemplate;
import com.mariabean.reservation.global.persistence.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "email_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailTemplateJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // comma-separated variable names: "name,reservationId,date"
    @Column(length = 500)
    private String variables;

    public EmailTemplate toDomain() {
        List<String> varList = (variables == null || variables.isBlank())
                ? List.of()
                : Arrays.asList(variables.split(","));
        return new EmailTemplate(id, name, subject, body, varList, getCreatedAt(), getUpdatedAt());
    }

    public static EmailTemplateJpaEntity fromDomain(EmailTemplate template) {
        String vars = template.getVariables() == null ? "" : String.join(",", template.getVariables());
        return EmailTemplateJpaEntity.builder()
                .id(template.getId())
                .name(template.getName())
                .subject(template.getSubject())
                .body(template.getBody())
                .variables(vars)
                .build();
    }

    public void update(String name, String subject, String body, List<String> variables) {
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.variables = variables == null ? "" : String.join(",", variables);
    }
}
