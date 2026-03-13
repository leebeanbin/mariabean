package com.mariabean.reservation.search.api;

import com.mariabean.reservation.facility.application.SymptomSpecialtyMapping;
import com.mariabean.reservation.facility.infrastructure.config.HiraSpecialtyConfig;
import com.mariabean.reservation.search.application.HospitalSearchService;
import com.mariabean.reservation.search.application.SearchService;
import com.mariabean.reservation.search.application.dto.HospitalSearchResult;
import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final HospitalSearchService hospitalSearchService;
    private final HiraSpecialtyConfig hiraSpecialtyConfig;
    private final SymptomSpecialtyMapping symptomSpecialtyMapping;

    /**
     * HIRA 진료과 목록 반환 (프론트 칩 렌더링용).
     * GET /api/v1/search/hospitals/specialties
     */
    @GetMapping("/hospitals/specialties")
    public CommonResponse<List<Map<String, String>>> getHiraSpecialties() {
        List<Map<String, String>> result = hiraSpecialtyConfig.getAllSpecialties().stream()
                .map(s -> Map.of("code", s.getCode(), "name", s.getName()))
                .collect(Collectors.toList());
        return CommonResponse.success(result);
    }

    /**
     * 증상 또는 진료과 코드 기반 근처 병원 검색.
     * GET /api/v1/search/hospitals/nearby
     *   ?lat=&lng=&radiusKm=5&specialties=01,13&symptom=headache&openNow=false&page=0&size=20
     */
    /**
     * 증상/진료과 코드 또는 자연어 쿼리("강남 정형외과") 기반 근처 병원 검색.
     * - query 우선: 있으면 specialties/symptom 무시하고 텍스트 검색
     * - symptom → 코드 변환 후 검색
     * - specialties → 코드 목록으로 직접 검색
     */
    @GetMapping("/hospitals/nearby")
    public CommonResponse<Page<HospitalSearchResult>> searchNearbyHospitals(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(required = false) String specialties,
            @RequestParam(required = false) String symptom,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean openNow,
            @PageableDefault(size = 20) Pageable pageable) {

        // textQuery 우선
        if (query != null && !query.isBlank()) {
            return CommonResponse.success(
                    hospitalSearchService.searchHospitals(lat, lng, radiusKm, List.of(), query, openNow, pageable));
        }

        List<String> codes;
        if (symptom != null && !symptom.isBlank()) {
            codes = symptomSpecialtyMapping.getCodes(symptom);
        } else if (specialties != null && !specialties.isBlank()) {
            codes = Arrays.asList(specialties.split(","));
        } else {
            codes = List.of();
        }

        return CommonResponse.success(
                hospitalSearchService.searchHospitals(lat, lng, radiusKm, codes, null, openNow, pageable));
    }

    /**
     * ResourceItem 키워드 검색 (Elasticsearch nori 형태소 분석).
     * GET /api/v1/search/resources?keyword=회의실&page=0&size=20
     */
    @GetMapping("/resources")
    public CommonResponse<Page<ResourceItemSearchDocument>> searchResources(
            @RequestParam("keyword") String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return CommonResponse.success(searchService.searchResourceByName(keyword, pageable));
    }

    /**
     * 현재 위치 기반 근처 Facility 검색.
     * GET
     * /api/v1/search/facilities/nearby?lat=37.5665&lng=126.9780&radiusKm=3&page=0&size=20
     */
    @GetMapping("/facilities/nearby")
    public CommonResponse<Page<FacilitySearchDocument>> searchNearbyFacilities(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        return CommonResponse.success(searchService.searchFacilitiesNearby(lat, lng, radiusKm, pageable));
    }

    /**
     * 화면 상단의 topLeft 좌표와 bottomRight 좌표를 이용해 화면 안의 시설물을 검색합니다.
     * 선택적으로 키워드 검색도 함께 지원합니다.
     * GET
     * /api/v1/search/facilities/box?topLeftLat=37.6&topLeftLng=126.9&bottomRightLat=37.5&bottomRightLng=127.0&keyword=카페
     */
    @GetMapping("/facilities/box")
    public CommonResponse<Page<FacilitySearchDocument>> searchFacilitiesInBox(
            @RequestParam double topLeftLat,
            @RequestParam double topLeftLng,
            @RequestParam double bottomRightLat,
            @RequestParam double bottomRightLng,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 50) Pageable pageable) {

        return CommonResponse.success(
                searchService.searchFacilitiesInBox(topLeftLat, topLeftLng, bottomRightLat, bottomRightLng, keyword,
                        pageable));
    }
}
