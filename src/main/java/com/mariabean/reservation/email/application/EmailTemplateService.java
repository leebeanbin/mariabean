package com.mariabean.reservation.email.application;

import com.mariabean.reservation.email.application.dto.EmailTemplateRequest;
import com.mariabean.reservation.email.application.dto.EmailTemplateResponse;
import com.mariabean.reservation.email.domain.EmailTemplate;
import com.mariabean.reservation.email.domain.EmailTemplateRepository;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository templateRepository;

    @Transactional
    public EmailTemplateResponse create(EmailTemplateRequest request) {
        EmailTemplate template = new EmailTemplate(
                null, request.name(), request.subject(), request.body(),
                request.variables(), null, null
        );
        return EmailTemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public EmailTemplateResponse update(Long id, EmailTemplateRequest request) {
        EmailTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        template.update(request.name(), request.subject(), request.body(), request.variables());
        return EmailTemplateResponse.from(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse getById(Long id) {
        return EmailTemplateResponse.from(
                templateRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND))
        );
    }

    @Transactional(readOnly = true)
    public Page<EmailTemplateResponse> getAll(Pageable pageable) {
        return templateRepository.findAll(pageable).map(EmailTemplateResponse::from);
    }

    @Transactional
    public void delete(Long id) {
        templateRepository.deleteById(id);
    }

    /**
     * 템플릿의 {{variableName}} 플레이스홀더를 실제 값으로 치환.
     */
    public String renderBody(Long templateId, Map<String, String> variables) {
        EmailTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return render(template.getBody(), variables);
    }

    public String renderSubject(Long templateId, Map<String, String> variables) {
        EmailTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return render(template.getSubject(), variables);
    }

    private String render(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
