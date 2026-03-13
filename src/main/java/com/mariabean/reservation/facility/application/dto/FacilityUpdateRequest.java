package com.mariabean.reservation.facility.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FacilityUpdateRequest {

    @NotBlank
    private String name;

    private String description;

    private Map<String, Object> metadata;
}
