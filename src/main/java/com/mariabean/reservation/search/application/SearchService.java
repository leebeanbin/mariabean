package com.mariabean.reservation.search.application;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

        private final ResourceItemSearchRepository searchRepository;
        private final ElasticsearchOperations elasticsearchOperations;
        private final ElasticsearchSyncService elasticsearchSyncService;
        private final AtomicBoolean facilitiesIndexMissingWarned = new AtomicBoolean(false);

        public List<ResourceItemSearchDocument> searchResourceByName(String keyword) {
                try {
                        return searchRepository.findByNameContaining(keyword);
                } catch (Exception e) {
                        log.warn("[Search] ES 연결 불가 – 빈 결과 반환: {}", e.getMessage());
                        return List.of();
                }
        }

        public Page<ResourceItemSearchDocument> searchResourceByName(String keyword, Pageable pageable) {
                try {
                        NativeQuery query = NativeQuery.builder()
                                        .withQuery(q -> q.match(m -> m.field("name").query(keyword)))
                                        .withPageable(pageable)
                                        .build();

                        SearchHits<ResourceItemSearchDocument> hits = elasticsearchOperations
                                        .search(query, ResourceItemSearchDocument.class);
                        List<ResourceItemSearchDocument> content = hits.stream()
                                        .map(SearchHit::getContent)
                                        .collect(Collectors.toList());
                        return new PageImpl<>(content, pageable, hits.getTotalHits());
                } catch (Exception e) {
                        log.warn("[Search] ES 연결 불가 – 빈 결과 반환: {}", e.getMessage());
                        return new PageImpl<>(List.of(), pageable, 0);
                }
        }

        @Cacheable(value = "searchResources", key = "#facilityId + '_' + #resourceType")
        public List<ResourceItemSearchDocument> searchByFacilityAndType(String facilityId, String resourceType) {
                try {
                        return searchRepository.findByFacilityIdAndResourceType(facilityId, resourceType);
                } catch (Exception e) {
                        log.warn("[Search] ES 연결 불가 – 빈 결과 반환: {}", e.getMessage());
                        return List.of();
                }
        }

        public List<FacilitySearchDocument> searchFacilitiesNearby(double lat, double lng, double radiusKm) {
                try {
                        Query geoQuery = Query.of(q -> q
                                        .geoDistance(gd -> gd
                                                        .field("location")
                                                        .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                                                        .distance(radiusKm + "km")));

                        NativeQuery nativeQuery = NativeQuery.builder()
                                        .withQuery(geoQuery)
                                        .build();

                        return elasticsearchOperations
                                        .search(nativeQuery, FacilitySearchDocument.class)
                                        .stream()
                                        .map(SearchHit::getContent)
                                        .collect(Collectors.toList());
                } catch (Exception e) {
                        log.warn("[Search] ES geo 검색 불가 – 빈 결과 반환: {}", e.getMessage());
                        return List.of();
                }
        }

        public Page<FacilitySearchDocument> searchFacilitiesNearby(double lat, double lng, double radiusKm,
                        Pageable pageable) {
                if (!isFacilitiesIndexAvailable()) {
                        return new PageImpl<>(List.of(), pageable, 0);
                }
                try {
                        Query geoQuery = Query.of(q -> q
                                        .geoDistance(gd -> gd
                                                        .field("location")
                                                        .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                                                        .distance(radiusKm + "km")));

                        NativeQuery nativeQuery = NativeQuery.builder()
                                        .withQuery(geoQuery)
                                        .withPageable(pageable)
                                        .build();

                        SearchHits<FacilitySearchDocument> hits = elasticsearchOperations
                                        .search(nativeQuery, FacilitySearchDocument.class);
                        List<FacilitySearchDocument> content = hits.stream()
                                        .map(SearchHit::getContent)
                                        .collect(Collectors.toList());
                        return new PageImpl<>(content, pageable, hits.getTotalHits());
                } catch (Exception e) {
                        log.warn("[Search] ES geo 검색 불가 – 빈 결과 반환: {}", e.getMessage());
                        return new PageImpl<>(List.of(), pageable, 0);
                }
        }

        public Page<FacilitySearchDocument> searchFacilitiesInBox(double topLeftLat, double topLeftLng,
                        double bottomRightLat, double bottomRightLng, String keyword, Pageable pageable) {
                if (!isFacilitiesIndexAvailable()) {
                        return new PageImpl<>(List.of(), pageable, 0);
                }
                try {
                        Query geoBoxQuery = Query.of(q -> q
                                        .geoBoundingBox(gb -> gb
                                                        .field("location")
                                                        .boundingBox(b -> b.tlbr(tb -> tb
                                                                        .topLeft(tl -> tl.latlon(
                                                                                        l -> l.lat(topLeftLat).lon(topLeftLng)))
                                                                        .bottomRight(br -> br.latlon(l -> l.lat(bottomRightLat)
                                                                                        .lon(bottomRightLng)))))));

                        Query finalQuery;
                        if (keyword != null && !keyword.isBlank()) {
                                finalQuery = Query.of(q -> q
                                                .bool(b -> b
                                                                .must(m -> m.match(match -> match.field("name").query(keyword)))
                                                                .filter(f -> f.geoBoundingBox(geoBoxQuery.geoBoundingBox()))));
                        } else {
                                finalQuery = geoBoxQuery;
                        }

                        NativeQuery nativeQuery = NativeQuery.builder()
                                        .withQuery(finalQuery)
                                        .withPageable(pageable)
                                        .build();

                        SearchHits<FacilitySearchDocument> hits = elasticsearchOperations
                                        .search(nativeQuery, FacilitySearchDocument.class);
                        List<FacilitySearchDocument> content = hits.stream()
                                        .map(SearchHit::getContent)
                                        .collect(Collectors.toList());
                        return new PageImpl<>(content, pageable, hits.getTotalHits());
                } catch (Exception e) {
                        log.warn("[Search] ES box 검색 불가 – 빈 결과 반환: {}", e.getMessage());
                        return new PageImpl<>(List.of(), pageable, 0);
                }
        }

        private boolean isFacilitiesIndexAvailable() {
                try {
                        boolean exists = elasticsearchOperations.indexOps(FacilitySearchDocument.class).exists();
                        if (!exists) {
                                boolean recovered = elasticsearchSyncService.ensureFacilitiesIndexAndBackfill();
                                if (recovered) {
                                        exists = elasticsearchOperations.indexOps(FacilitySearchDocument.class).exists();
                                }
                        }
                        if (exists && facilitiesIndexMissingWarned.get()) {
                                facilitiesIndexMissingWarned.set(false);
                        }
                        if (!exists && facilitiesIndexMissingWarned.compareAndSet(false, true)) {
                                log.warn("[Search] ES index 'facilities'가 없어 box/nearby 검색은 빈 결과를 반환합니다.");
                        }
                        return exists;
                } catch (Exception e) {
                        if (facilitiesIndexMissingWarned.compareAndSet(false, true)) {
                                log.warn("[Search] ES index 상태 확인 실패 – 빈 결과 반환: {}", e.getMessage());
                        }
                        return false;
                }
        }
}
