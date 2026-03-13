package com.mariabean.reservation.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentProvider {

    KAKAO_PAY("카카오페이"),
    TOSS_PAY("토스페이");

    private final String displayName;
}
