package com.mariabean.reservation.reservation.application.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WaitingInfoResponse {

    private Long reservationId;
    private int queuePosition;
    private Integer estimatedWaitMinutes;
    private int totalActiveReservations;
}
