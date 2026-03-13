package com.mariabean.reservation.search.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Map;

@Getter
@Document(indexName = "resource_items", createIndex = false)
public class ResourceItemSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String facilityId;

    @Field(type = FieldType.Text, analyzer = "nori") // Using 'nori' for Korean morphological analysis, fallback to
                                                     // standard if not installed. In prod, 'nori' makes Korean search
                                                     // much better.
    private String name;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Integer)
    private Integer limitCapacity;

    @Field(type = FieldType.Integer)
    private Integer floor;

    @Field(type = FieldType.Keyword)
    private String location;

    @Field(type = FieldType.Object)
    private Map<String, Object> customAttributes;

    @Builder
    public ResourceItemSearchDocument(String id, String facilityId, String name, String resourceType,
            Integer limitCapacity, Integer floor, String location,
            Map<String, Object> customAttributes) {
        this.id = id;
        this.facilityId = facilityId;
        this.name = name;
        this.resourceType = resourceType;
        this.limitCapacity = limitCapacity;
        this.floor = floor;
        this.location = location;
        this.customAttributes = customAttributes;
    }
}
