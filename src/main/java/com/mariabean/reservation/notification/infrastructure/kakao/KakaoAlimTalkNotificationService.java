package com.mariabean.reservation.notification.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAlimTalkNotificationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final KakaoMessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final KakaoMessageTemplateFactory templateFactory;

    public boolean sendPaymentConfirmation(NotificationRecipient recipient, Long reservationId, BigDecimal amount, String provider) {
        String message = templateFactory.paymentConfirmationText(reservationId, amount, provider);
        return send(recipient, message, templateFactory.paymentConfirmationVariables(reservationId, amount, provider));
    }

    public boolean sendReservationCancellation(NotificationRecipient recipient, Long reservationId) {
        String message = templateFactory.reservationCancellationText(reservationId);
        return send(recipient, message, templateFactory.reservationCancellationVariables(reservationId));
    }

    private boolean send(NotificationRecipient recipient, String message, java.util.Map<String, String> templateVariables) {
        KakaoMessagingProperties.Channel channel = properties.getAlimtalk();
        if (!channel.isEnabled()) {
            return false;
        }
        if (recipient.phoneNumber() == null || recipient.phoneNumber().isBlank()) {
            log.debug("[KakaoAlimTalk] phone number is missing. memberId={}", recipient.memberId());
            return false;
        }
        if (channel.getEndpoint() == null || channel.getEndpoint().isBlank()) {
            log.warn("[KakaoAlimTalk] endpoint is not configured.");
            return false;
        }

        try {
            String payload = resolvePayload(channel, recipient, message, templateVariables);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(channel.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (channel.getToken() != null && !channel.getToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + channel.getToken());
            }
            if (channel.getApiKey() != null && !channel.getApiKey().isBlank()) {
                requestBuilder.header("X-API-KEY", channel.getApiKey());
                // NCP SENS 계열에서는 X-Secret-Key를 사용합니다.
                requestBuilder.header("X-Secret-Key", channel.getApiKey());
            }

            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }

            log.warn("[KakaoAlimTalk] send failed. status={}, body={}", response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("[KakaoAlimTalk] send error: {}", e.getMessage());
            return false;
        }
    }

    private String resolvePayload(
            KakaoMessagingProperties.Channel channel,
            NotificationRecipient recipient,
            String message,
            java.util.Map<String, String> templateVariables) throws Exception {
        if (channel.getProvider() == KakaoMessagingProperties.Provider.NCP_SENS) {
            KakaoPayloads.NcpAlimTalkRequest request = templateFactory.ncpAlimTalkRequest(
                    recipient,
                    channel.getSenderKey(),
                    channel.getTemplateCode(),
                    message);
            return objectMapper.writeValueAsString(request);
        }

        KakaoPayloads.AlimTalkRequest request = templateFactory.alimTalkRequest(
                recipient,
                channel.getSenderKey(),
                channel.getTemplateCode(),
                message,
                templateVariables);
        return objectMapper.writeValueAsString(request);
    }
}
