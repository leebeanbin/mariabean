package com.mariabean.reservation.facility.application.dto;

import com.mariabean.reservation.facility.domain.Facility;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FacilityResponse {
    private String id;
    private String name;
    private String category;
    private String description;
    private String placeId;
    private Double latitude;
    private Double longitude;
    private String address;
    private Map<String, Object> metadata;
    private List<String> specialties;

    public static FacilityResponse from(Facility facility) {
        return FacilityResponse.builder()
                .id(facility.getId())
                .name(facility.getName())
                .category(facility.getCategory())
                .description(facility.getDescription())
                .placeId(facility.getPlaceId())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .address(facility.getAddress())
                .metadata(facility.getMetadata())
                .specialties(facility.getSpecialties())
                .build();
    }
}
