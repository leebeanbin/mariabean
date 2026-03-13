package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class HospitalSearchResult {

    private String facilityId;          // null = Kakao 외부 결과
    private String placeId;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private List<String> specialties;
    private Boolean openNow;            // null = 운영시간 미등록
    private Map<String, Object> operatingHours;
    private String source;              // "INTERNAL" | "KAKAO"
    private int rank;
}
