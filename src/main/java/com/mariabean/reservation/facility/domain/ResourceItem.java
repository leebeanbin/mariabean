package com.mariabean.reservation.facility.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure Domain Model for ResourceItem. No Spring/Database annotations.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceItem {

    private String id;
    private String facilityId;
    private String name;
    private String resourceType;
    private Integer limitCapacity;
    private Integer floor;
    private String location;
    private Integer estimatedWaitMinutes;
    private Map<String, Object> customAttributes = new HashMap<>();

    @Builder
    public ResourceItem(String id, String facilityId, String name, String resourceType,
            Integer limitCapacity, Integer floor, String location,
            Integer estimatedWaitMinutes, Map<String, Object> customAttributes) {
        this.id = id;
        this.facilityId = facilityId;
        this.name = name;
        this.resourceType = resourceType;
        this.limitCapacity = limitCapacity;
        this.floor = floor;
        this.location = location;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
        if (customAttributes != null) {
            this.customAttributes = customAttributes;
        }
    }

    public void updateEstimatedWaitMinutes(Integer minutes) {
        this.estimatedWaitMinutes = minutes;
    }

    public void updateDetails(String name, String resourceType, Integer limitCapacity,
            Integer floor, String location, Map<String, Object> customAttributes) {
        if (name != null)
            this.name = name;
        if (resourceType != null)
            this.resourceType = resourceType;
        if (limitCapacity != null)
            this.limitCapacity = limitCapacity;
        if (floor != null)
            this.floor = floor;
        if (location != null)
            this.location = location;
        if (customAttributes != null)
            this.customAttributes = customAttributes;
    }
}
