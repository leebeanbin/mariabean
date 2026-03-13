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
import java.time.Duration;
import java.util.UUID;

/**
 * 카카오페이 PG 연동.
 * kakaopay.enabled=true 일 때 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kakaopay.enabled", havingValue = "true", matchIfMissing = false)
public class KakaoPayGateway implements PgGateway {

    private static final String BASE_URL = "https://open-api.kakaopay.com/online/v1/payment";

    @Value("${kakaopay.secret-key}")
    private String secretKey;

    @Value("${kakaopay.cid:TC0ONETIME}")
    private String cid;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public PgReadyResult ready(Long reservationId, BigDecimal amount, String orderId) {
        try {
            String body = "cid=" + cid
                    + "&partner_order_id=" + orderId
                    + "&partner_user_id=" + reservationId
                    + "&item_name=MariBean예약"
                    + "&quantity=1"
                    + "&total_amount=" + amount.intValue()
                    + "&tax_free_amount=0"
                    + "&approval_url=" + frontendUrl + "/reservations/" + reservationId + "?pg_token={pg_token}"
                    + "&cancel_url=" + frontendUrl + "/reservations/" + reservationId
                    + "&fail_url=" + frontendUrl + "/reservations/" + reservationId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/ready"))
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[KakaoPay] ready failed: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
            }

            JsonNode node = objectMapper.readTree(response.body());
            String tid = node.path("tid").asText();
            String redirectUrl = node.path("next_redirect_pc_url").asText();
            String mobileUrl = node.path("next_redirect_mobile_url").asText();

            log.info("[KakaoPay] ready success. tid={}, orderId={}", tid, orderId);
            return PgReadyResult.builder()
                    .tid(tid)
                    .redirectUrl(redirectUrl)
                    .mobileRedirectUrl(mobileUrl)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[KakaoPay] ready exception: orderId={}", orderId, e);
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }

    @Override
    public String approve(String pgToken, String tid) {
        try {
            // tid는 ready 응답에서 받은 거래 ID, pgToken은 콜백 파라미터
            // partner_order_id, partner_user_id는 결제 레코드에서 복원 필요
            // — 여기서는 tid를 파싱해 orderId를 추출하거나 Redis에서 조회
            String orderId = resolveOrderIdFromTid(tid);

            String body = "cid=" + cid
                    + "&tid=" + tid
                    + "&partner_order_id=" + orderId
                    + "&partner_user_id=member"
                    + "&pg_token=" + pgToken;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/approve"))
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[KakaoPay] approve failed: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
            }

            JsonNode node = objectMapper.readTree(response.body());
            String aid = node.path("aid").asText(); // 카카오 승인번호
            log.info("[KakaoPay] approve success. aid={}, tid={}", aid, tid);
            return aid;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[KakaoPay] approve exception: tid={}", tid, e);
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }

    @Override
    public void cancel(String transactionId, BigDecimal cancelAmount) {
        try {
            // transactionId = tid (결제 준비 시 발급된 거래번호)
            String body = "cid=" + cid
                    + "&tid=" + transactionId
                    + "&cancel_amount=" + cancelAmount.intValue()
                    + "&cancel_tax_free_amount=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/cancel"))
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[KakaoPay] cancel failed: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
            }
            log.info("[KakaoPay] cancel success. tid={}", transactionId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[KakaoPay] cancel exception: tid={}", transactionId, e);
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }
    }

    private String resolveOrderIdFromTid(String tid) {
        // tid 기반으로 orderId 조회 — PaymentService에서 tid를 저장하므로 여기서는 단순 변환
        return tid;
    }
}
