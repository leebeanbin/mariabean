package com.mariabean.reservation.payment.infrastructure.external.pg;

import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.domain.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgGatewayFactory {

    private final ApplicationContext applicationContext;
    private final StubPgGateway stubPgGateway;

    public PgGateway getGateway(PaymentProvider provider) {
        return switch (provider) {
            case KAKAO_PAY -> resolveBean(KakaoPayGateway.class);
            case TOSS_PAY -> resolveBean(TossPayGateway.class);
        };
    }

    private PgGateway resolveBean(Class<? extends PgGateway> type) {
        try {
            return applicationContext.getBean(type);
        } catch (Exception e) {
            log.warn("[PgGatewayFactory] {} not configured, falling back to StubPgGateway", type.getSimpleName());
            return stubPgGateway;
        }
    }
}
