package com.mariabean.reservation.facility.api;

import com.mariabean.reservation.facility.application.ResourceItemService;
import com.mariabean.reservation.facility.application.dto.ResourceItemCreateRequest;
import com.mariabean.reservation.facility.application.dto.ResourceItemUpdateRequest;
import com.mariabean.reservation.facility.application.dto.ResourceItemResponse;
import com.mariabean.reservation.global.response.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceItemController {

    private final ResourceItemService resourceItemService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public CommonResponse<ResourceItemResponse> registerResource(
            @RequestBody @Valid ResourceItemCreateRequest request) {
        ResourceItemResponse response = ResourceItemResponse.from(resourceItemService.registerResource(request));
        return CommonResponse.success(response);
    }

    @GetMapping("/facility/{facilityId}")
    public CommonResponse<Page<ResourceItemResponse>> getResourcesByFacility(
            @PathVariable("facilityId") String facilityId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ResourceItemResponse> responses = resourceItemService.getResourcesByFacility(facilityId, pageable)
                .map(ResourceItemResponse::from);
        return CommonResponse.success(responses);
    }

    @GetMapping("/facility/{facilityId}/floor/{floor}")
    public CommonResponse<Page<ResourceItemResponse>> getResourcesByFloor(
            @PathVariable("facilityId") String facilityId,
            @PathVariable("floor") Integer floor,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ResourceItemResponse> responses = resourceItemService
                .getResourcesByFacilityAndFloor(facilityId, floor, pageable)
                .map(ResourceItemResponse::from);
        return CommonResponse.success(responses);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{resourceId}/wait-time")
    public CommonResponse<ResourceItemResponse> updateWaitTime(
            @PathVariable("resourceId") String resourceId,
            @RequestParam("minutes") Integer minutes) {
        return CommonResponse.success(
                ResourceItemResponse.from(resourceItemService.updateEstimatedWaitTime(resourceId, minutes)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{resourceId}")
    public CommonResponse<ResourceItemResponse> updateResource(
            @PathVariable("resourceId") String resourceId,
            @RequestBody @Valid ResourceItemUpdateRequest request) {
        return CommonResponse.success(
                ResourceItemResponse.from(resourceItemService.updateResource(resourceId, request)));
    }

    @GetMapping("/{resourceId}")
    public CommonResponse<ResourceItemResponse> getResource(@PathVariable("resourceId") String resourceId) {
        return CommonResponse.success(
                ResourceItemResponse.from(resourceItemService.getResource(resourceId)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{resourceId}")
    public CommonResponse<Void> deleteResource(@PathVariable("resourceId") String resourceId) {
        resourceItemService.deleteResource(resourceId);
        return CommonResponse.success(null);
    }
}
