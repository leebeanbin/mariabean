package com.mariabean.reservation.event.kafka;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain Event published to Kafka after payment approval.
 */
@Getter
@Builder
public class PaymentApprovedEvent {

    private Long paymentId;
    private Long reservationId;
    private Long memberId;
    private BigDecimal amount;
    private String provider;
    private String transactionId;
    private LocalDateTime approvedAt;

    public static final String TOPIC = "payment-approved";
}
