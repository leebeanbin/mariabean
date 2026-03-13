package com.mariabean.reservation.facility.application.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Google Places API 검색/상세조회 결과를 담는 DTO.
 * FacilityCreateRequest의 자동 보완에 사용된다.
 */
@Getter
@Builder
public class PlaceSearchResult {
    private String placeId;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String provider; // INTERNAL, NAVER_LOCAL, NAVER_GEOCODE, GOOGLE
    private String sourceFacilityId; // 내부 시설 매칭 시 facilityId
    private String matchType; // INTERNAL, ADDRESS, PLACE
}
