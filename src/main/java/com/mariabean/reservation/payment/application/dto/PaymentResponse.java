package com.mariabean.reservation.payment.application.dto;

import com.mariabean.reservation.payment.domain.Payment;
import com.mariabean.reservation.payment.domain.PaymentProvider;
import com.mariabean.reservation.payment.domain.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long reservationId;
    private PaymentProvider provider;
    private PaymentStatus status;
    private BigDecimal amount;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .reservationId(payment.getReservationId())
                .provider(payment.getProvider())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .approvedAt(payment.getApprovedAt())
                .build();
    }
}
