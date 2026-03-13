package com.mariabean.reservation.payment.infrastructure.external.pg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.application.pg.PgReadyResult;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 토스페이먼츠 PG 연동.
 * tosspay.enabled=true 일 때 활성화.
 *
 * 플로우:
 * 1. ready() → orderId 반환 (토스는 클라이언트 SDK에서 결제창 오픈, 서버 ready 없음)
 * 2. 클라이언트 SDK 결제 완료 → successUrl?paymentKey=...&orderId=...&amount=...
 * 3. approve(paymentKey, orderId) → 서버 승인
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tosspay.enabled", havingValue = "true", matchIfMissing = false)
public class TossPayGateway implements PgGateway {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/%s/cancel";

    @Value("${tosspay.secret-key}")
    private String secretKey;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 토스는 서버 ready 단계 없음.
     * 클라이언트 SDK 호출에 필요한 orderId와 successUrl/failUrl을 반환.
     */
    @Override
    public PgReadyResult ready(Long reservationId, BigDecimal amount, String orderId) {
        log.info("[TossPay] ready: reservationId={}, amount={}, orderId={}", reservationId, amount, orderId);
        String successUrl = frontendUrl + "/reservations/" + reservationId + "?toss_success=true";
        String failUrl = frontendUrl + "/reservations/" + reservationId + "?toss_fail=true";

        return PgReadyResult.builder()
                .tid(orderId)
                .redirectUrl(successUrl)    // 클라이언트 SDK successUrl
                .mobileRedirectUrl(failUrl) // 클라이언트 SDK failUrl
                .clientKey(secretKey.replace("_sk_", "_ck_")) // 토스 클라이언트 키 변환
                .build();
    }

    /**
     * 결제 승인: pgToken = paymentKey (클라이언트 SDK 콜백 파라미터)
     */
    @Override
    public String approve(String pgToken, String tid) {
        try {
            // tid = orderId, pgToken = paymentKey
            String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("paymentKey", pgToken);
                put("orderId", tid);
                put("amount", 0); // PaymentService에서 amount를 전달받도록 개선 필요
            }});

            String encoded = Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CONFIRM_URL))
                    .header("Authorization", "Basic " + encoded)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[TossPay] approve failed: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
            }

            JsonNode node = objectMapper.readTree(response.body());
            String paymentKey = node.path("paymentKey").asText();
            log.info("[TossPay] approve success. paymentKey={}", paymentKey);
            return paymentKey;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossPay] approve exception: pgToken={}", pgToken, e);
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }

    @Override
    public void cancel(String transactionId, BigDecimal cancelAmount) {
        try {
            // transactionId = paymentKey
            String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("cancelReason", "사용자 취소");
                put("cancelAmount", cancelAmount.intValue());
            }});

            String encoded = Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(CANCEL_URL, transactionId)))
                    .header("Authorization", "Basic " + encoded)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[TossPay] cancel failed: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
            }
            log.info("[TossPay] cancel success. paymentKey={}", transactionId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossPay] cancel exception: paymentKey={}", transactionId, e);
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }
    }
}
