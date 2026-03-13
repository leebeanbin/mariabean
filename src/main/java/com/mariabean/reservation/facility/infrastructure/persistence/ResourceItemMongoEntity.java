package com.mariabean.reservation.facility.infrastructure.persistence;

import com.mariabean.reservation.global.persistence.BaseMongoTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "resource_items")
public class ResourceItemMongoEntity extends BaseMongoTimeEntity {

    @Id
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
    public ResourceItemMongoEntity(String id, String facilityId, String name, String resourceType,
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
}
