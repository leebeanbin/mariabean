package com.mariabean.reservation.email.api;

import com.mariabean.reservation.email.application.EmailSendService;
import com.mariabean.reservation.email.application.dto.ImmediateEmailRequest;
import com.mariabean.reservation.email.application.dto.ScheduledEmailRequest;
import com.mariabean.reservation.email.application.dto.ScheduledEmailResponse;
import com.mariabean.reservation.email.domain.ScheduledEmailStatus;
import com.mariabean.reservation.global.response.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/email")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmailSendController {

    private final EmailSendService emailSendService;

    /** 즉시 발송 */
    @PostMapping("/send")
    public CommonResponse<Boolean> sendImmediate(@RequestBody @Valid ImmediateEmailRequest request) {
        return CommonResponse.success(emailSendService.sendImmediate(request));
    }

    /** 예약 발송 등록 */
    @PostMapping("/schedule")
    public CommonResponse<ScheduledEmailResponse> schedule(@RequestBody @Valid ScheduledEmailRequest request) {
        return CommonResponse.success(emailSendService.schedule(request));
    }

    /** 예약 발송 목록 조회 */
    @GetMapping("/scheduled")
    public CommonResponse<Page<ScheduledEmailResponse>> getScheduled(
            @RequestParam(required = false) ScheduledEmailStatus status,
            @PageableDefault(size = 20, sort = "scheduledAt") Pageable pageable) {
        if (status != null) {
            return CommonResponse.success(emailSendService.getByStatus(status, pageable));
        }
        return CommonResponse.success(emailSendService.getAll(pageable));
    }
}
