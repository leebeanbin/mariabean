package com.mariabean.reservation.payment.api;

import com.mariabean.reservation.payment.application.PaymentService;
import com.mariabean.reservation.payment.application.dto.PaymentApproveRequest;
import com.mariabean.reservation.payment.application.dto.PaymentReadyRequest;
import com.mariabean.reservation.payment.application.dto.PaymentResponse;
import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.auth.application.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/ready")
    public CommonResponse<PaymentResponse> readyPayment(@RequestBody @Valid PaymentReadyRequest request) {
        return CommonResponse.success(
                PaymentResponse.from(paymentService.readyPayment(request, SecurityUtils.getCurrentMemberId())));
    }

    @PostMapping("/approve")
    public CommonResponse<PaymentResponse> approvePayment(@RequestBody @Valid PaymentApproveRequest request) {
        return CommonResponse.success(
                PaymentResponse.from(paymentService.approvePayment(request)));
    }

    @PostMapping("/{paymentId}/cancel")
    public CommonResponse<PaymentResponse> cancelPayment(@PathVariable("paymentId") Long paymentId) {
        return CommonResponse.success(
                PaymentResponse.from(paymentService.cancelPayment(paymentId)));
    }

    @GetMapping("/{paymentId}")
    public CommonResponse<PaymentResponse> getPayment(@PathVariable("paymentId") Long paymentId) {
        return CommonResponse.success(
                PaymentResponse.from(paymentService.getPayment(paymentId)));
    }

    @GetMapping("/reservation/{reservationId}")
    public CommonResponse<PaymentResponse> getPaymentByReservation(@PathVariable("reservationId") Long reservationId) {
        return CommonResponse.success(
                PaymentResponse.from(paymentService.getPaymentByReservation(reservationId)));
    }
}
