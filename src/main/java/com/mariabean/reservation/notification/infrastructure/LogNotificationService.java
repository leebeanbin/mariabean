package com.mariabean.reservation.notification.infrastructure;

import com.mariabean.reservation.notification.application.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 알림 서비스 로그 구현체 (개발/스텁용).
 *
 * 실서비스 전환 시 이 클래스를 아래 구현체로 교체:
 * - KakaoAlimTalkNotificationService: 카카오 알림톡 API 연동
 * - EmailNotificationService: Spring Mail + 템플릿 엔진 연동
 *
 * 카카오 알림톡 API: https://business.kakao.com/info/bizmessage/
 */
@Slf4j
@Service
public class LogNotificationService implements NotificationService {

    @Override
    public void sendPaymentConfirmation(Long memberId, Long reservationId, BigDecimal amount, String provider) {
        // TODO: 카카오 알림톡 / 이메일 발송
        // KakaoAlimTalk: POST https://kakaoapi.aligo.in/akv10/alimtalk/send/
        // 템플릿 코드: PAYMENT_CONFIRMED
        log.info("[Notification] 결제 확인 알림 - memberId={}, reservationId={}, amount={}, provider={}",
                memberId, reservationId, amount, provider);
    }

    @Override
    public void sendReservationCancellation(Long memberId, Long reservationId) {
        // TODO: 카카오 알림톡 / 이메일 발송
        // 템플릿 코드: RESERVATION_CANCELLED
        log.info("[Notification] 예약 취소 알림 - memberId={}, reservationId={}", memberId, reservationId);
    }
}
