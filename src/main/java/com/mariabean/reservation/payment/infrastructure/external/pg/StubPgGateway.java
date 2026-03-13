package com.mariabean.reservation.payment.infrastructure.external.pg;

import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.application.pg.PgReadyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * KakaoPay/TossPay 미설정 시 동작하는 스텁 구현체.
 * 개발/테스트 환경에서 결제 흐름 확인용.
 */
@Slf4j
@Component
public class StubPgGateway implements PgGateway {

    @Override
    public PgReadyResult ready(Long reservationId, BigDecimal amount, String orderId) {
        log.warn("[StubPg] STUB ready - 실제 PG 미연동. reservationId={}, amount={}", reservationId, amount);
        String fakeTid = "STUB-" + System.currentTimeMillis();
        return PgReadyResult.builder()
                .tid(fakeTid)
                .redirectUrl("http://localhost:3000/reservations/" + reservationId)
                .mobileRedirectUrl("http://localhost:3000/reservations/" + reservationId)
                .build();
    }

    @Override
    public String approve(String pgToken, String tid) {
        log.warn("[StubPg] STUB approve - tid={}, pgToken={}", tid, pgToken);
        return "STUB-APPROVED-" + System.currentTimeMillis();
    }

    @Override
    public void cancel(String transactionId, BigDecimal cancelAmount) {
        log.warn("[StubPg] STUB cancel - transactionId={}", transactionId);
    }
}
