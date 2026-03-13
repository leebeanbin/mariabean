package com.mariabean.reservation.facility.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class FacilityMedicalUpdateRequest {

    /** HIRA 진료과 코드 목록 (예: ["01", "13"]) */
    private List<String> specialties;

    /**
     * 운영시간 맵.
     * 예: {"MON": {"open": "09:00", "close": "18:00"}, "SUN": null}
     */
    private Map<String, Object> operatingHours;
}
