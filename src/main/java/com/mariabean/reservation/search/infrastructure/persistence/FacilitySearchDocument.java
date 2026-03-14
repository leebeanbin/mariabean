package com.mariabean.reservation.search.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
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
 * embedding: nomic-embed-text 768차원 벡터 (kNN 하이브리드 검색용)
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

    /** nomic-embed-text 768차원 벡터. ES에서 dense_vector type으로 매핑 필요. */
    @Setter
    @Field(type = FieldType.Object, index = false, store = false)
    private float[] embedding;

    @Builder
    public FacilitySearchDocument(String id, String name, String category,
                                   String address, String placeId, GeoPoint location,
                                   List<String> specialties, float[] embedding) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.address = address;
        this.placeId = placeId;
        this.location = location;
        if (specialties != null) {
            this.specialties = specialties;
        }
        this.embedding = embedding;
    }
}
