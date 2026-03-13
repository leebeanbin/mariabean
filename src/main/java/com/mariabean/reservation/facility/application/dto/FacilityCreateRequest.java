package com.mariabean.reservation.facility.application.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FacilityCreateRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String category;
    
    private String description;
    
    // placeId가 있으면 Google Places API로 자동 보완, 없으면 직접 입력값 사용
    private String placeId;
    
    private Double latitude;
    private Double longitude;
    private String address;

    private Map<String, Object> metadata;
}
