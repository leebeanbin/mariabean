package com.mariabean.reservation.notification.infrastructure.kakao;

import com.mariabean.reservation.notification.application.NotificationRecipient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class KakaoMessageTemplateFactory {

    public String paymentConfirmationText(Long reservationId, BigDecimal amount, String provider) {
        return String.format(
                "[MariBean] 결제가 완료되었습니다. 예약번호=%d, 결제수단=%s, 금액=%s",
                reservationId, provider, amount);
    }

    public Map<String, String> paymentConfirmationVariables(Long reservationId, BigDecimal amount, String provider) {
        return Map.of(
                "reservationId", String.valueOf(reservationId),
                "provider", provider,
                "amount", amount.toPlainString());
    }

    public String reservationCancellationText(Long reservationId) {
        return String.format("[MariBean] 예약이 취소되었습니다. 예약번호=%d", reservationId);
    }

    public Map<String, String> reservationCancellationVariables(Long reservationId) {
        return Map.of("reservationId", String.valueOf(reservationId));
    }

    public KakaoPayloads.AlimTalkRequest alimTalkRequest(
            NotificationRecipient recipient,
            String senderKey,
            String templateCode,
            String message,
            Map<String, String> variables) {
        return new KakaoPayloads.AlimTalkRequest(
                senderKey,
                templateCode,
                List.of(new KakaoPayloads.AlimTalkMessage(
                        recipient.phoneNumber(),
                        message,
                        variables)));
    }

    public KakaoPayloads.NcpAlimTalkRequest ncpAlimTalkRequest(
            NotificationRecipient recipient,
            String plusFriendId,
            String templateCode,
            String message) {
        return new KakaoPayloads.NcpAlimTalkRequest(
                plusFriendId,
                templateCode,
                List.of(new KakaoPayloads.NcpAlimTalkMessage(
                        recipient.phoneNumber(),
                        message)));
    }

    public KakaoPayloads.TalkMessageRequest talkMessageRequest(
            NotificationRecipient recipient,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            String message) {
        try {
            String receiverUuids = objectMapper.writeValueAsString(List.of(recipient.kakaoProviderId()));
            String templateObject = objectMapper.writeValueAsString(
                    new KakaoPayloads.TalkTemplateObject("text", message));
            return new KakaoPayloads.TalkMessageRequest(receiverUuids, templateObject);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Kakao talk message payload", e);
        }
    }
}
