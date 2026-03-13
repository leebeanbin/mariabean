package com.mariabean.reservation.facility.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class ResourceItemUpdateRequest {
    private String name;
    private String resourceType;
    private Integer limitCapacity;
    private Integer floor;
    private String location;
    private Map<String, Object> customAttributes;
}
