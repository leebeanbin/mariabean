package com.mariabean.reservation.payment.domain;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure Domain Model for Payment.
 * Contains core business rules for payment state transitions.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    private Long id;
    private Long version;  // 낙관적 락용 — JPA @Version과 매핑됨
    private Long reservationId;
    private Long memberId;

    private PaymentProvider provider;
    private PaymentStatus status;

    private BigDecimal amount;
    private String transactionId;     // PG사 고유 트랜잭션 ID (tid)
    private String approvalToken;     // PG사 승인용 토큰 (pg_token 등)

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    @Builder
    public Payment(Long id, Long version, Long reservationId, Long memberId,
                   PaymentProvider provider, PaymentStatus status,
                   BigDecimal amount, String transactionId, String approvalToken,
                   LocalDateTime createdAt, LocalDateTime approvedAt) {
        this.id = id;
        this.version = version;
        this.reservationId = reservationId;
        this.memberId = memberId;
        this.provider = provider;
        this.status = (status != null) ? status : PaymentStatus.READY;
        this.amount = amount;
        this.transactionId = transactionId;
        this.approvalToken = approvalToken;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
        this.approvedAt = approvedAt;
    }

    // --- Domain Business Rules ---

    public void approve(String transactionId) {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.PAYMENT_STATUS_INVALID);
        }
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail() {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.PAYMENT_STATUS_INVALID);
        }
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public void refund() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }
        this.status = PaymentStatus.REFUNDED;
    }
}
