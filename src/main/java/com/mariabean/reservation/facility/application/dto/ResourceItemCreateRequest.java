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
public class ResourceItemCreateRequest {
    @NotBlank
    private String facilityId;

    @NotBlank
    private String name;

    @NotBlank
    private String resourceType; // e.g., ROOM, TICKET, TIME_SLOT

    private Integer limitCapacity;

    private Integer floor;

    private String location;

    private Map<String, Object> customAttributes;
}
