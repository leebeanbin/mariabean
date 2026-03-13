package com.mariabean.reservation.notification.application;

import java.math.BigDecimal;

/**
 * 알림 발송 서비스 인터페이스.
 * 구현체를 교체하여 카카오 알림톡, 이메일 등 다양한 채널로 전환 가능.
 */
public interface NotificationService {

    void sendPaymentConfirmation(Long memberId, Long reservationId, BigDecimal amount, String provider);

    void sendReservationCancellation(Long memberId, Long reservationId);
}
