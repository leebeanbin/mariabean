package com.mariabean.reservation.payment.application.pg;

import java.math.BigDecimal;

/**
 * PG(결제대행사) 연동 인터페이스.
 * KakaoPay, TossPay 등 각 PG사 구현체로 교체 가능.
 */
public interface PgGateway {

    /**
     * 결제 준비 요청. PG사 서버에 결제 정보를 등록하고 결제 URL을 반환.
     *
     * @return 사용자에게 리다이렉트할 결제 페이지 URL
     */
    PgReadyResult ready(Long reservationId, BigDecimal amount, String orderId);

    /**
     * 결제 승인 요청. 사용자 결제 완료 후 PG사 콜백의 pg_token으로 최종 승인.
     *
     * @param pgToken PG사가 콜백으로 전달한 승인 토큰
     * @param tid     결제 준비 시 PG사가 발급한 거래 고유번호
     * @return PG사 트랜잭션 ID (transactionId)
     */
    String approve(String pgToken, String tid);

    /**
     * 결제 취소/환불 요청.
     *
     * @param transactionId 승인된 거래의 PG사 트랜잭션 ID
     */
    void cancel(String transactionId, BigDecimal cancelAmount);
}
