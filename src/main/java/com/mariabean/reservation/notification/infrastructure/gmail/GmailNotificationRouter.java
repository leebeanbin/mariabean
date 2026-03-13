package com.mariabean.reservation.notification.infrastructure.gmail;

import com.mariabean.reservation.notification.application.NotificationRecipient;
import com.mariabean.reservation.notification.application.NotificationRecipientReader;
import com.mariabean.reservation.notification.application.NotificationService;
import com.mariabean.reservation.notification.infrastructure.LogNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Gmail 이메일 기반 알림 라우터.
 * app.notification.channel=gmail 로 설정 시 Primary로 동작.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "gmail", matchIfMissing = true)
public class GmailNotificationRouter implements NotificationService {

    private final NotificationRecipientReader recipientReader;
    private final GmailNotificationService gmailNotificationService;
    private final LogNotificationService logNotificationService;

    @Override
    public void sendPaymentConfirmation(Long memberId, Long reservationId, BigDecimal amount, String provider) {
        Optional<NotificationRecipient> recipientOpt = recipientReader.findByMemberId(memberId);
        if (recipientOpt.isEmpty()) {
            log.warn("[GmailNotificationRouter] recipient not found. memberId={}", memberId);
            logNotificationService.sendPaymentConfirmation(memberId, reservationId, amount, provider);
            return;
        }
        NotificationRecipient recipient = recipientOpt.get();
        if (!gmailNotificationService.sendPaymentConfirmation(recipient, reservationId, amount, provider)) {
            logNotificationService.sendPaymentConfirmation(memberId, reservationId, amount, provider);
        }
    }

    @Override
    public void sendReservationCancellation(Long memberId, Long reservationId) {
        Optional<NotificationRecipient> recipientOpt = recipientReader.findByMemberId(memberId);
        if (recipientOpt.isEmpty()) {
            log.warn("[GmailNotificationRouter] recipient not found. memberId={}", memberId);
            logNotificationService.sendReservationCancellation(memberId, reservationId);
            return;
        }
        NotificationRecipient recipient = recipientOpt.get();
        if (!gmailNotificationService.sendReservationCancellation(recipient, reservationId)) {
            logNotificationService.sendReservationCancellation(memberId, reservationId);
        }
    }
}
