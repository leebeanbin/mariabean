package com.mariabean.reservation.notification.infrastructure.gmail;

import com.mariabean.reservation.notification.application.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Gmail API를 통한 이메일 알림 서비스.
 * NotificationRouter에서 위임 호출됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailNotificationService {

    @Value("${app.email.admin-member-id:0}")
    private Long adminMemberId;

    private final GmailEmailSender gmailEmailSender;

    public boolean sendPaymentConfirmation(NotificationRecipient recipient, Long reservationId,
                                           BigDecimal amount, String provider) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            log.warn("[GmailNotificationService] recipient has no email. memberId={}", recipient.memberId());
            return false;
        }
        String subject = "[MariBean] 결제가 완료되었습니다";
        String body = buildPaymentConfirmationHtml(recipient.name(), reservationId, amount, provider);
        return gmailEmailSender.send(adminMemberId, recipient.email(), subject, body);
    }

    public boolean sendReservationCancellation(NotificationRecipient recipient, Long reservationId) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            log.warn("[GmailNotificationService] recipient has no email. memberId={}", recipient.memberId());
            return false;
        }
        String subject = "[MariBean] 예약이 취소되었습니다";
        String body = buildCancellationHtml(recipient.name(), reservationId);
        return gmailEmailSender.send(adminMemberId, recipient.email(), subject, body);
    }

    private String buildPaymentConfirmationHtml(String name, Long reservationId,
                                                 BigDecimal amount, String provider) {
        return """
                <div style="font-family: 'Pretendard Variable', 'Inter', sans-serif; max-width: 520px; margin: 0 auto; background: #FCFCFD; border-radius: 16px; overflow: hidden; border: 1px solid #E4E4E7;">
                  <div style="background: linear-gradient(135deg, #5E6AD2, #7C3AED); padding: 32px 28px; text-align: center;">
                    <h1 style="color: #fff; font-size: 22px; margin: 0; font-weight: 700;">결제 완료</h1>
                  </div>
                  <div style="padding: 28px;">
                    <p style="color: #18181B; font-size: 15px; margin-bottom: 20px;">안녕하세요, <strong>%s</strong>님!</p>
                    <p style="color: #52525B; font-size: 14px; line-height: 1.6;">결제가 성공적으로 완료되었습니다.</p>
                    <div style="background: #F4F4F5; border-radius: 12px; padding: 20px; margin: 20px 0;">
                      <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                        <span style="color: #71717A; font-size: 13px;">예약 번호</span>
                        <span style="color: #18181B; font-size: 13px; font-weight: 600;">#%s</span>
                      </div>
                      <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                        <span style="color: #71717A; font-size: 13px;">결제 금액</span>
                        <span style="color: #18181B; font-size: 13px; font-weight: 600;">%s원</span>
                      </div>
                      <div style="display: flex; justify-content: space-between;">
                        <span style="color: #71717A; font-size: 13px;">결제 수단</span>
                        <span style="color: #18181B; font-size: 13px; font-weight: 600;">%s</span>
                      </div>
                    </div>
                  </div>
                  <div style="padding: 16px 28px; border-top: 1px solid #E4E4E7; text-align: center;">
                    <p style="color: #A1A1AA; font-size: 11px; margin: 0;">MariBean · 시설 예약 플랫폼</p>
                  </div>
                </div>
                """.formatted(name, reservationId, amount.toPlainString(), provider);
    }

    private String buildCancellationHtml(String name, Long reservationId) {
        return """
                <div style="font-family: 'Pretendard Variable', 'Inter', sans-serif; max-width: 520px; margin: 0 auto; background: #FCFCFD; border-radius: 16px; overflow: hidden; border: 1px solid #E4E4E7;">
                  <div style="background: linear-gradient(135deg, #18181B, #1E293B); padding: 32px 28px; text-align: center;">
                    <h1 style="color: #fff; font-size: 22px; margin: 0; font-weight: 700;">예약 취소</h1>
                  </div>
                  <div style="padding: 28px;">
                    <p style="color: #18181B; font-size: 15px; margin-bottom: 20px;">안녕하세요, <strong>%s</strong>님!</p>
                    <p style="color: #52525B; font-size: 14px; line-height: 1.6;">
                      예약 번호 <strong>#%s</strong>이 취소되었습니다.<br>
                      다시 예약을 원하시면 MariBean을 방문해주세요.
                    </p>
                  </div>
                  <div style="padding: 16px 28px; border-top: 1px solid #E4E4E7; text-align: center;">
                    <p style="color: #A1A1AA; font-size: 11px; margin: 0;">MariBean · 시설 예약 플랫폼</p>
                  </div>
                </div>
                """.formatted(name, reservationId);
    }
}
