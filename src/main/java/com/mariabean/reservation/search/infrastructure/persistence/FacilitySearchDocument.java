package com.mariabean.reservation.search.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch Facility 인덱스 문서.
 * location 필드에 GeoPoint를 저장해 geo_distance 쿼리를 지원한다.
 * createIndex=false: 인덱스는 nori 플러그인이 있는 운영 ES에서 별도 생성.
 */
@Getter
@Document(indexName = "facilities", createIndex = false)
public class FacilitySearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String name;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Text)
    private String address;

    @Field(type = FieldType.Keyword)
    private String placeId;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Keyword)
    private List<String> specialties = new ArrayList<>();

    @Builder
    public FacilitySearchDocument(String id, String name, String category,
                                   String address, String placeId, GeoPoint location,
                                   List<String> specialties) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.address = address;
        this.placeId = placeId;
        this.location = location;
        if (specialties != null) {
            this.specialties = specialties;
        }
    }
}
