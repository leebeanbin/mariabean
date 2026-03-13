package com.mariabean.reservation.payment.application.dto;

import com.mariabean.reservation.payment.domain.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentReadyRequest {

    @NotNull
    private Long reservationId;

    @NotNull
    private PaymentProvider provider;

    @NotNull
    private BigDecimal amount;
}
