package com.mariabean.reservation.facility.api;

import com.mariabean.reservation.facility.application.FacilityService;
import com.mariabean.reservation.facility.application.MapService;
import com.mariabean.reservation.facility.application.analytics.MapAnalyticsQueryService;
import com.mariabean.reservation.facility.application.dto.FacilityCreateRequest;
import com.mariabean.reservation.facility.application.dto.FacilityMedicalUpdateRequest;
import com.mariabean.reservation.facility.application.dto.FacilityUpdateRequest;
import com.mariabean.reservation.facility.application.dto.FacilityResponse;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.auth.application.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;
    private final MapService mapService;
    private final MapAnalyticsQueryService mapAnalyticsQueryService;

    /**
     * Facility 등록.
     * - placeId 포함 시: 외부 지도 API(Google/Naver 조합)로 이름/주소/좌표 자동 보완
     * - placeId 없을 시: name, address, latitude, longitude 직접 입력
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public CommonResponse<FacilityResponse> registerFacility(
            @RequestBody @Valid FacilityCreateRequest request) {
        FacilityResponse response = FacilityResponse.from(
                facilityService.registerFacility(request, SecurityUtils.getCurrentMemberId()));
        return CommonResponse.success(response);
    }

    @GetMapping("/{facilityId}")
    public CommonResponse<FacilityResponse> getFacility(
            @PathVariable String facilityId) {
        return CommonResponse.success(FacilityResponse.from(facilityService.getFacility(facilityId)));
    }

    @GetMapping
    public CommonResponse<Page<FacilityResponse>> getFacilitiesByCategory(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FacilityResponse> responses = facilityService.getFacilitiesByCategory(category, pageable)
                .map(FacilityResponse::from);
        return CommonResponse.success(responses);
    }

    @PutMapping("/{facilityId}")
    public CommonResponse<FacilityResponse> updateFacility(
            @PathVariable String facilityId,
            @RequestBody @Valid FacilityUpdateRequest request) {
        return CommonResponse.success(FacilityResponse.from(
                facilityService.updateFacility(facilityId, request, SecurityUtils.getCurrentMemberId())));
    }

    @PreAuthorize("hasRole('ADMIN') or @facilityService.isOwner(#facilityId, authentication.principal.memberId)")
    @PatchMapping("/{facilityId}/medical")
    public CommonResponse<FacilityResponse> updateFacilityMedical(
            @PathVariable String facilityId,
            @RequestBody FacilityMedicalUpdateRequest request) {
        return CommonResponse.success(FacilityResponse.from(
                facilityService.updateFacilityMedical(facilityId, request, SecurityUtils.getCurrentMemberId())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{facilityId}")
    public CommonResponse<Void> deleteFacility(@PathVariable String facilityId) {
        facilityService.deleteFacility(facilityId, SecurityUtils.getCurrentMemberId());
        return CommonResponse.success(null);
    }

    /**
     * 지도 검색 텍스트 API — Facility 등록 전 장소 후보 목록 조회.
     * GET /api/v1/facilities/places/search?query=신촌세브란스
     */
    @GetMapping("/places/search")
    public CommonResponse<List<PlaceSearchResult>> searchPlaces(
            @RequestParam String query) {
        return CommonResponse.success(mapService.searchPlaces(query));
    }

    @GetMapping("/places/popular")
    public CommonResponse<List<String>> getPopularKeywords(
            @RequestParam(defaultValue = "8") int size
    ) {
        return CommonResponse.success(mapAnalyticsQueryService.getPopularKeywords(size));
    }

    @GetMapping("/places/click")
    public CommonResponse<Void> recordSuggestionClick(
            @RequestParam String query,
            @RequestParam(required = false) String queryType,
            @RequestParam(required = false) String matchType
    ) {
        mapService.recordSuggestionClick(query, queryType, matchType);
        return CommonResponse.success(null);
    }

    /**
     * place 상세 정보 조회 — 선택한 placeId의 이름/주소/좌표 반환.
     * GET /api/v1/facilities/places/{placeId}
     */
    @GetMapping("/places/{placeId}")
    public CommonResponse<PlaceSearchResult> getPlaceDetails(
            @PathVariable String placeId) {
        return CommonResponse.success(mapService.getPlaceDetails(placeId));
    }
}
