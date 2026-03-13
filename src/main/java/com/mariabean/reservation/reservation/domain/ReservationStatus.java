package com.mariabean.reservation.reservation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {

    PENDING("대기"),
    CONFIRMED("확정"),
    CANCELLED("취소"),
    EXPIRED("만료");

    private final String description;
}
