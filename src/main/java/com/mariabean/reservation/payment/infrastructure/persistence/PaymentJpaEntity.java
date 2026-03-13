package com.mariabean.reservation.payment.infrastructure.persistence;

import com.mariabean.reservation.payment.domain.PaymentProvider;
import com.mariabean.reservation.payment.domain.PaymentStatus;
import com.mariabean.reservation.global.persistence.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_reservation", columnList = "reservationId"),
        @Index(name = "idx_payment_member", columnList = "memberId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Setter
    private Long version;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private String transactionId;
    private String approvalToken;

    private LocalDateTime approvedAt;

    @Builder
    public PaymentJpaEntity(Long id, Long version, Long reservationId, Long memberId,
            PaymentProvider provider, PaymentStatus status,
            BigDecimal amount, String transactionId, String approvalToken,
            LocalDateTime createdAt, LocalDateTime approvedAt) {
        this.id = id;
        this.version = version;
        this.reservationId = reservationId;
        this.memberId = memberId;
        this.provider = provider;
        this.status = status;
        this.amount = amount;
        this.transactionId = transactionId;
        this.approvalToken = approvalToken;
        this.approvedAt = approvedAt;
    }
}
