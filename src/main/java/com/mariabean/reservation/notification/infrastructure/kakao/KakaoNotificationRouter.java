package com.mariabean.reservation.notification.infrastructure.kakao;

import com.mariabean.reservation.notification.application.NotificationRecipient;
import com.mariabean.reservation.notification.application.NotificationRecipientReader;
import com.mariabean.reservation.notification.application.NotificationService;
import com.mariabean.reservation.notification.infrastructure.LogNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "kakao")
public class KakaoNotificationRouter implements NotificationService {

    private final NotificationRecipientReader recipientReader;
    private final KakaoAlimTalkNotificationService alimTalkService;
    private final KakaoTalkMessageNotificationService talkMessageService;
    private final LogNotificationService logNotificationService;

    @Override
    public void sendPaymentConfirmation(Long memberId, Long reservationId, BigDecimal amount, String provider) {
        Optional<NotificationRecipient> recipientOpt = recipientReader.findByMemberId(memberId);
        if (recipientOpt.isEmpty()) {
            log.warn("[NotificationRouter] recipient not found. memberId={}", memberId);
            logNotificationService.sendPaymentConfirmation(memberId, reservationId, amount, provider);
            return;
        }

        NotificationRecipient recipient = recipientOpt.get();
        if (alimTalkService.sendPaymentConfirmation(recipient, reservationId, amount, provider)) {
            return;
        }
        if (talkMessageService.sendPaymentConfirmation(recipient, reservationId, amount, provider)) {
            return;
        }

        logNotificationService.sendPaymentConfirmation(memberId, reservationId, amount, provider);
    }

    @Override
    public void sendReservationCancellation(Long memberId, Long reservationId) {
        Optional<NotificationRecipient> recipientOpt = recipientReader.findByMemberId(memberId);
        if (recipientOpt.isEmpty()) {
            log.warn("[NotificationRouter] recipient not found. memberId={}", memberId);
            logNotificationService.sendReservationCancellation(memberId, reservationId);
            return;
        }

        NotificationRecipient recipient = recipientOpt.get();
        if (alimTalkService.sendReservationCancellation(recipient, reservationId)) {
            return;
        }
        if (talkMessageService.sendReservationCancellation(recipient, reservationId)) {
            return;
        }

        logNotificationService.sendReservationCancellation(memberId, reservationId);
    }
}
