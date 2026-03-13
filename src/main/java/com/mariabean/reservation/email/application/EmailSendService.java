package com.mariabean.reservation.email.application;

import com.mariabean.reservation.email.application.dto.ImmediateEmailRequest;
import com.mariabean.reservation.email.application.dto.ScheduledEmailRequest;
import com.mariabean.reservation.email.application.dto.ScheduledEmailResponse;
import com.mariabean.reservation.email.domain.ScheduledEmail;
import com.mariabean.reservation.email.domain.ScheduledEmailRepository;
import com.mariabean.reservation.email.domain.ScheduledEmailStatus;
import com.mariabean.reservation.notification.infrastructure.gmail.GmailEmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendService {

    @Value("${app.email.admin-member-id:0}")
    private Long adminMemberId;

    private final GmailEmailSender gmailEmailSender;
    private final EmailTemplateService templateService;
    private final ScheduledEmailRepository scheduledEmailRepository;

    /**
     * 즉시 발송
     */
    public boolean sendImmediate(ImmediateEmailRequest request) {
        String subject = templateService.renderSubject(request.templateId(), request.variables());
        String body = templateService.renderBody(request.templateId(), request.variables());
        boolean result = gmailEmailSender.send(adminMemberId, request.recipientEmail(), subject, body);
        log.info("[EmailSendService] immediate send to={}, templateId={}, success={}",
                request.recipientEmail(), request.templateId(), result);
        return result;
    }

    /**
     * 예약 발송 등록
     */
    @Transactional
    public ScheduledEmailResponse schedule(ScheduledEmailRequest request) {
        ScheduledEmail email = new ScheduledEmail(
                null,
                request.templateId(),
                request.recipientMemberId(),
                request.recipientEmail(),
                request.scheduledAt(),
                request.variables(),
                ScheduledEmailStatus.PENDING,
                null,
                null
        );
        return ScheduledEmailResponse.from(scheduledEmailRepository.save(email));
    }

    @Transactional(readOnly = true)
    public Page<ScheduledEmailResponse> getAll(Pageable pageable) {
        return scheduledEmailRepository.findAll(pageable).map(ScheduledEmailResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ScheduledEmailResponse> getByStatus(ScheduledEmailStatus status, Pageable pageable) {
        return scheduledEmailRepository.findByStatus(status, pageable).map(ScheduledEmailResponse::from);
    }
}
