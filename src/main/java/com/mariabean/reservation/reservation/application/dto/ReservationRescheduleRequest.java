package com.mariabean.reservation.reservation.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReservationRescheduleRequest {

    @NotNull
    private LocalDateTime newStartTime;

    @NotNull
    private LocalDateTime newEndTime;
}
