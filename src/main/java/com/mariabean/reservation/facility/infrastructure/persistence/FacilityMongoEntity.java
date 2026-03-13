package com.mariabean.reservation.facility.infrastructure.persistence;

import com.mariabean.reservation.global.persistence.BaseMongoTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "facilities")
public class FacilityMongoEntity extends BaseMongoTimeEntity {

    @Id
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
    public FacilityMongoEntity(String id, String name, String category, String description, Long ownerMemberId,
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
}
