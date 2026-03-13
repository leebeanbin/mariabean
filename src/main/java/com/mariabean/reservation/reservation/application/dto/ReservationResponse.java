package com.mariabean.reservation.reservation.application.dto;

import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReservationResponse {

    private Long id;
    private Long memberId;
    private String resourceItemId;
    private String facilityId;
    private String seatLabel;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .memberId(reservation.getMemberId())
                .resourceItemId(reservation.getResourceItemId())
                .facilityId(reservation.getFacilityId())
                .seatLabel(reservation.getSeatLabel())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
