package com.mariabean.reservation.facility.application.dto;

import com.mariabean.reservation.facility.domain.ResourceItem;
import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class ResourceItemResponse {
    private String id;
    private String facilityId;
    private String name;
    private String resourceType;
    private Integer limitCapacity;
    private Integer floor;
    private String location;
    private Integer estimatedWaitMinutes;
    private Map<String, Object> customAttributes;

    public static ResourceItemResponse from(ResourceItem item) {
        return ResourceItemResponse.builder()
                .id(item.getId())
                .facilityId(item.getFacilityId())
                .name(item.getName())
                .resourceType(item.getResourceType())
                .limitCapacity(item.getLimitCapacity())
                .floor(item.getFloor())
                .location(item.getLocation())
                .estimatedWaitMinutes(item.getEstimatedWaitMinutes())
                .customAttributes(item.getCustomAttributes())
                .build();
    }
}
