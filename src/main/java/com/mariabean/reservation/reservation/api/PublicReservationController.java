package com.mariabean.reservation.reservation.api;

import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.reservation.application.ReservationService;
import com.mariabean.reservation.reservation.application.dto.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 비인증 사용자에게 공개되는 예약 가용성 조회 API.
 * SecurityConfig에서 /api/v1/public/** 은 permitAll().
 */
@RestController
@RequestMapping("/api/v1/public/reservations")
@RequiredArgsConstructor
public class PublicReservationController {

    private final ReservationService reservationService;

    /**
     * 특정 리소스의 날짜별 가용 시간 슬롯 조회 (30분 단위, 09:00~21:00).
     */
    @GetMapping("/availability")
    public CommonResponse<AvailabilityResponse> getAvailability(
            @RequestParam String resourceItemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CommonResponse.success(reservationService.getAvailability(resourceItemId, date));
    }
}
