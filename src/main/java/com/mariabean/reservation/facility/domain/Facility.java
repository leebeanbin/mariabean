package com.mariabean.reservation.facility.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Domain Model for Facility. No Spring/Database annotations.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility {

    private String id;
    private String name;
    private String category;
    private String description;
    private Long ownerMemberId;
    
    private String placeId;
    private Double latitude;
    private Double longitude;
    private String address;

    private Map<String, Object> metadata = new HashMap<>();

    private List<String> specialties = new ArrayList<>();

    @Builder
    public Facility(String id, String name, String category, String description, Long ownerMemberId,
                    String placeId, Double latitude, Double longitude, String address,
                    Map<String, Object> metadata, List<String> specialties) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.ownerMemberId = ownerMemberId;
        this.placeId = placeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        if (metadata != null) {
            this.metadata = metadata;
        }
        if (specialties != null) {
            this.specialties = specialties;
        }
    }

    public void updateDetails(String name, String description, Map<String, Object> metadata) {
        this.name = name;
        this.description = description;
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    public void updateMedicalInfo(List<String> specialties, Map<String, Object> operatingHours) {
        if (specialties != null) {
            this.specialties = specialties;
        }
        if (operatingHours != null) {
            this.metadata.put("operatingHours", operatingHours);
        }
    }
}
