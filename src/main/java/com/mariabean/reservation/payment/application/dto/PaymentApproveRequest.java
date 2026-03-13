package com.mariabean.reservation.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentApproveRequest {

    @NotNull
    private Long paymentId;

    @NotBlank
    private String pgToken;  // PG사 승인 토큰
}
