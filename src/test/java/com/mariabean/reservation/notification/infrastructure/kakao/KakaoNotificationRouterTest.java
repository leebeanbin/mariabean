package com.mariabean.reservation.notification.infrastructure.kakao;

import com.mariabean.reservation.notification.application.NotificationRecipient;
import com.mariabean.reservation.notification.application.NotificationRecipientReader;
import com.mariabean.reservation.notification.infrastructure.LogNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KakaoNotificationRouterTest {

    @Mock
    private NotificationRecipientReader recipientReader;
    @Mock
    private KakaoAlimTalkNotificationService alimTalkService;
    @Mock
    private KakaoTalkMessageNotificationService talkMessageService;
    @Mock
    private LogNotificationService logNotificationService;

    @InjectMocks
    private KakaoNotificationRouter router;

    @Test
    @DisplayName("결제 알림: 알림톡 성공 시 fallback을 호출하지 않는다")
    void sendPaymentConfirmation_alimTalkSuccess() {
        Long memberId = 1L;
        Long reservationId = 10L;
        NotificationRecipient recipient = new NotificationRecipient(memberId, "kim", "01012341234", "kakao_123");
        given(recipientReader.findByMemberId(memberId)).willReturn(Optional.of(recipient));
        given(alimTalkService.sendPaymentConfirmation(recipient, reservationId, BigDecimal.TEN, "KAKAO_PAY"))
                .willReturn(true);

        router.sendPaymentConfirmation(memberId, reservationId, BigDecimal.TEN, "KAKAO_PAY");

        verify(alimTalkService).sendPaymentConfirmation(recipient, reservationId, BigDecimal.TEN, "KAKAO_PAY");
    }

    @Test
    @DisplayName("결제 알림: 알림톡 실패 시 talk_message fallback을 호출한다")
    void sendPaymentConfirmation_fallbackToTalkMessage() {
        Long memberId = 2L;
        Long reservationId = 20L;
        NotificationRecipient recipient = new NotificationRecipient(memberId, "lee", "01056785678", "kakao_456");
        given(recipientReader.findByMemberId(memberId)).willReturn(Optional.of(recipient));
        given(alimTalkService.sendPaymentConfirmation(recipient, reservationId, BigDecimal.ONE, "TOSS_PAY"))
                .willReturn(false);
        given(talkMessageService.sendPaymentConfirmation(recipient, reservationId, BigDecimal.ONE, "TOSS_PAY"))
                .willReturn(true);

        router.sendPaymentConfirmation(memberId, reservationId, BigDecimal.ONE, "TOSS_PAY");

        verify(alimTalkService).sendPaymentConfirmation(recipient, reservationId, BigDecimal.ONE, "TOSS_PAY");
        verify(talkMessageService).sendPaymentConfirmation(recipient, reservationId, BigDecimal.ONE, "TOSS_PAY");
    }

    @Test
    @DisplayName("취소 알림: 수신자가 없으면 로그 fallback으로 전송한다")
    void sendReservationCancellation_noRecipient() {
        Long memberId = 3L;
        Long reservationId = 30L;
        given(recipientReader.findByMemberId(memberId)).willReturn(Optional.empty());

        router.sendReservationCancellation(memberId, reservationId);

        verify(logNotificationService).sendReservationCancellation(memberId, reservationId);
    }
}
