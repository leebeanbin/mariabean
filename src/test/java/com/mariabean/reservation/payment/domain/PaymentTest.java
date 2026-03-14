package com.mariabean.reservation.payment.domain;

import com.mariabean.reservation.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment createReadyPayment() {
        return Payment.builder()
                .id(1L)
                .reservationId(100L)
                .memberId(10L)
                .provider(PaymentProvider.KAKAO_PAY)
                .amount(BigDecimal.valueOf(50000))
                .build();
    }

    @Test
    @DisplayName("새로 생성된 결제의 초기 상태는 READY이다")
    void newPayment_shouldBe_READY() {
        Payment payment = createReadyPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("READY 상태에서 승인 시 APPROVED로 전환된다")
    void approve_fromReady_success() {
        Payment payment = createReadyPayment();
        payment.approve("TXN-123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo("TXN-123");
        assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하면 예외 발생")
    void approve_fromApproved_throwsException() {
        Payment payment = createReadyPayment();
        payment.approve("TXN-123");
        assertThatThrownBy(() -> payment.approve("TXN-456"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("APPROVED 상태에서 취소 가능")
    void cancel_fromApproved_success() {
        Payment payment = createReadyPayment();
        payment.approve("TXN-123");
        payment.cancel();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("READY 상태에서 취소 시도하면 예외 발생")
    void cancel_fromReady_throwsException() {
        Payment payment = createReadyPayment();
        assertThatThrownBy(payment::cancel)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("READY 상태에서 실패 처리 가능")
    void fail_fromReady_success() {
        Payment payment = createReadyPayment();
        payment.fail();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("APPROVED 상태에서 환불 가능")
    void refund_fromApproved_success() {
        Payment payment = createReadyPayment();
        payment.approve("TXN-123");
        payment.refund();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
