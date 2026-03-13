package com.mariabean.reservation.notification.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoTalkMessageNotificationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final KakaoMessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final KakaoMessageTemplateFactory templateFactory;

    public boolean sendPaymentConfirmation(NotificationRecipient recipient, Long reservationId, BigDecimal amount, String provider) {
        String message = templateFactory.paymentConfirmationText(reservationId, amount, provider);
        return send(recipient, message);
    }

    public boolean sendReservationCancellation(NotificationRecipient recipient, Long reservationId) {
        String message = templateFactory.reservationCancellationText(reservationId);
        return send(recipient, message);
    }

    private boolean send(NotificationRecipient recipient, String message) {
        KakaoMessagingProperties.Channel channel = properties.getTalkMessage();
        if (!channel.isEnabled()) {
            return false;
        }
        if (recipient.kakaoProviderId() == null || recipient.kakaoProviderId().isBlank()) {
            log.debug("[KakaoTalkMessage] providerId is missing. memberId={}", recipient.memberId());
            return false;
        }
        if (channel.getEndpoint() == null || channel.getEndpoint().isBlank()) {
            log.warn("[KakaoTalkMessage] endpoint is not configured.");
            return false;
        }

        try {
            KakaoPayloads.TalkMessageRequest body = templateFactory.talkMessageRequest(
                    recipient,
                    objectMapper,
                    message);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(channel.getEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toFormBody(body)));

            if (channel.getToken() != null && !channel.getToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + channel.getToken());
            }
            if (channel.getApiKey() != null && !channel.getApiKey().isBlank()) {
                requestBuilder.header("X-API-KEY", channel.getApiKey());
            }

            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }

            log.warn("[KakaoTalkMessage] send failed. status={}, body={}", response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("[KakaoTalkMessage] send error: {}", e.getMessage());
            return false;
        }
    }

    private String toFormBody(KakaoPayloads.TalkMessageRequest body) {
        return "receiver_uuids=" + urlEncode(body.receiverUuids())
                + "&template_object=" + urlEncode(body.templateObject());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
