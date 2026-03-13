package com.mariabean.reservation.notification.infrastructure.gmail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailEmailSender {

    private static final String GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final GmailTokenStore tokenStore;
    private final GmailTokenRefresher tokenRefresher;

    /**
     * Gmail API를 통해 HTML 이메일 발송.
     *
     * @param senderMemberId 발송자 memberId (Gmail token 보유 관리자)
     * @param to             수신자 이메일
     * @param subject        제목
     * @param htmlBody       HTML 본문
     * @return 발송 성공 여부
     */
    public boolean send(Long senderMemberId, String to, String subject, String htmlBody) {
        GmailTokenEntry token = tokenStore.findByMemberId(senderMemberId).orElse(null);
        if (token == null) {
            log.warn("[GmailEmailSender] No token for memberId={}", senderMemberId);
            return false;
        }

        String accessToken = resolveAccessToken(token);
        if (accessToken == null) {
            log.warn("[GmailEmailSender] Could not obtain access token for memberId={}", senderMemberId);
            return false;
        }

        try {
            String rawMessage = buildRawMessage(to, subject, htmlBody);
            String encoded = Base64.getUrlEncoder().encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));
            String body = "{\"raw\":\"" + encoded + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GMAIL_SEND_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("[GmailEmailSender] Email sent to={}, subject={}", to, subject);
                return true;
            }
            log.warn("[GmailEmailSender] Send failed. status={}, body={}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("[GmailEmailSender] Exception during send to={}", to, e);
        }
        return false;
    }

    private String resolveAccessToken(GmailTokenEntry token) {
        // access token이 있으면 먼저 시도, 없거나 만료됐으면 refresh
        if (token.accessToken() != null && !token.accessToken().isBlank()) {
            return token.accessToken();
        }
        // access token 없음 → refresh
        return tokenRefresher.refresh(token.refreshToken())
                .map(newToken -> {
                    // 새 토큰을 Redis에 갱신 (3600초)
                    tokenStore.save(token.memberId(), newToken, token.refreshToken(), 3600L);
                    return newToken;
                })
                .orElse(null);
    }

    private static String buildRawMessage(String to, String subject, String htmlBody) {
        return "To: " + to + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n"
                + "\r\n"
                + htmlBody;
    }
}
