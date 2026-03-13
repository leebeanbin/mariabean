package com.mariabean.reservation.email.application;

import com.mariabean.reservation.email.domain.ScheduledEmail;
import com.mariabean.reservation.email.domain.ScheduledEmailRepository;
import com.mariabean.reservation.email.infrastructure.persistence.ScheduledEmailJpaRepository;
import com.mariabean.reservation.notification.infrastructure.gmail.GmailEmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 매 60초마다 PENDING 상태의 예약 이메일을 발송.
 * Redisson 분산 락으로 다중 인스턴스 환경에서 중복 실행 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledEmailProcessor {

    private static final String LOCK_KEY = "email:scheduled:processor";
    private static final long LOCK_WAIT_SECONDS = 0L;   // 이미 실행 중이면 스킵
    private static final long LOCK_LEASE_SECONDS = 55L; // 60초 주기보다 약간 짧게

    @Value("${app.email.admin-member-id:0}")
    private Long adminMemberId;

    private final ScheduledEmailRepository scheduledEmailRepository;
    private final ScheduledEmailJpaRepository jpaRepository;
    private final EmailTemplateService templateService;
    private final GmailEmailSender gmailEmailSender;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 60_000)
    public void processPendingEmails() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (!acquired) {
            log.debug("[ScheduledEmailProcessor] Lock not acquired — another instance is running");
            return;
        }

        try {
            processAll();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    protected void processAll() {
        List<ScheduledEmail> dues = scheduledEmailRepository.findPendingDue(LocalDateTime.now());
        if (dues.isEmpty()) return;

        log.info("[ScheduledEmailProcessor] Processing {} pending emails", dues.size());
        for (ScheduledEmail email : dues) {
            processOne(email);
        }
    }

    private void processOne(ScheduledEmail email) {
        var entityOpt = jpaRepository.findById(email.getId());
        if (entityOpt.isEmpty()) return;
        var entity = entityOpt.get();

        // 이미 처리된 경우 스킵 (동시 실행 경합 방어)
        if (entity.getStatus() != com.mariabean.reservation.email.domain.ScheduledEmailStatus.PENDING) {
            return;
        }

        try {
            String subject = templateService.renderSubject(email.getTemplateId(), email.getVariables());
            String body = templateService.renderBody(email.getTemplateId(), email.getVariables());
            boolean sent = gmailEmailSender.send(adminMemberId, email.getRecipientEmail(), subject, body);

            if (sent) {
                entity.markSent();
                log.info("[ScheduledEmailProcessor] Sent emailId={} to={}", email.getId(), email.getRecipientEmail());
            } else {
                entity.markFailed("Gmail send returned false");
                log.warn("[ScheduledEmailProcessor] Failed emailId={}", email.getId());
            }
        } catch (Exception e) {
            entity.markFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            log.error("[ScheduledEmailProcessor] Exception for emailId={}", email.getId(), e);
        }

        jpaRepository.save(entity);
    }
}
