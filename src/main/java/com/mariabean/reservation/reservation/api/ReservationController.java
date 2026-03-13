package com.mariabean.reservation.reservation.api;

import com.mariabean.reservation.reservation.application.ReservationService;
import com.mariabean.reservation.reservation.application.dto.ReservationCreateRequest;
import com.mariabean.reservation.reservation.application.dto.ReservationRescheduleRequest;
import com.mariabean.reservation.reservation.application.dto.ReservationResponse;
import com.mariabean.reservation.reservation.application.dto.WaitingInfoResponse;
import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.auth.application.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public CommonResponse<ReservationResponse> createReservation(@RequestBody @Valid ReservationCreateRequest request) {
        ReservationResponse response = ReservationResponse.from(
                reservationService.createReservation(request, SecurityUtils.getCurrentMemberId()));
        return CommonResponse.success(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{reservationId}/confirm")
    public CommonResponse<ReservationResponse> confirmReservation(@PathVariable("reservationId") Long reservationId) {
        return CommonResponse.success(
                ReservationResponse.from(reservationService.confirmReservation(reservationId)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{reservationId}/reschedule")
    public CommonResponse<ReservationResponse> rescheduleReservation(
            @PathVariable("reservationId") Long reservationId,
            @RequestBody @Valid ReservationRescheduleRequest request) {
        return CommonResponse.success(
                ReservationResponse.from(reservationService.rescheduleReservation(reservationId, request)));
    }

    @PostMapping("/{reservationId}/cancel")
    public CommonResponse<ReservationResponse> cancelReservation(@PathVariable("reservationId") Long reservationId) {
        return CommonResponse.success(
                ReservationResponse.from(reservationService.cancelReservation(reservationId)));
    }

    @GetMapping("/{reservationId}")
    public CommonResponse<ReservationResponse> getReservation(@PathVariable("reservationId") Long reservationId) {
        return CommonResponse.success(
                ReservationResponse.from(reservationService.getReservation(reservationId)));
    }

    @GetMapping("/{reservationId}/waiting")
    public CommonResponse<WaitingInfoResponse> getWaitingInfo(@PathVariable("reservationId") Long reservationId) {
        return CommonResponse.success(reservationService.getWaitingInfo(reservationId));
    }

    @GetMapping("/my")
    public CommonResponse<Page<ReservationResponse>> getMyReservations(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ReservationResponse> responses = reservationService
                .getMyReservations(SecurityUtils.getCurrentMemberId(), pageable)
                .map(ReservationResponse::from);
        return CommonResponse.success(responses);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public CommonResponse<Page<ReservationResponse>> getAllReservations(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ReservationResponse> responses = reservationService
                .getAllReservations(pageable)
                .map(ReservationResponse::from);
        return CommonResponse.success(responses);
    }

    /**
     * 관리자 전용: 대기열에서 다음 손님을 즉시 호출 (일찍 부르기).
     * 해당 리소스의 가장 오래된 PENDING 예약을 CONFIRMED 로 변경.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/resource/{resourceItemId}/call-next")
    public CommonResponse<ReservationResponse> callNext(
            @PathVariable("resourceItemId") String resourceItemId) {
        return CommonResponse.success(
                ReservationResponse.from(reservationService.callNext(resourceItemId)));
    }
}
