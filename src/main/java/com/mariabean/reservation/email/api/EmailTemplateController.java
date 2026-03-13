package com.mariabean.reservation.email.api;

import com.mariabean.reservation.email.application.EmailTemplateService;
import com.mariabean.reservation.email.application.dto.EmailTemplateRequest;
import com.mariabean.reservation.email.application.dto.EmailTemplateResponse;
import com.mariabean.reservation.global.response.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/email/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmailTemplateController {

    private final EmailTemplateService templateService;

    @GetMapping
    public CommonResponse<Page<EmailTemplateResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return CommonResponse.success(templateService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public CommonResponse<EmailTemplateResponse> getById(@PathVariable Long id) {
        return CommonResponse.success(templateService.getById(id));
    }

    @PostMapping
    public CommonResponse<EmailTemplateResponse> create(@RequestBody @Valid EmailTemplateRequest request) {
        return CommonResponse.success(templateService.create(request));
    }

    @PutMapping("/{id}")
    public CommonResponse<EmailTemplateResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid EmailTemplateRequest request) {
        return CommonResponse.success(templateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public CommonResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return CommonResponse.success();
    }
}
