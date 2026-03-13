package com.mariabean.reservation.search.application;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.mariabean.reservation.facility.application.SymptomSpecialtyMapping;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.facility.infrastructure.config.HiraSpecialtyConfig;
import com.mariabean.reservation.facility.infrastructure.external.map.KakaoLocalSearchClient;
import com.mariabean.reservation.search.application.dto.HospitalSearchResult;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalSearchService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CATEGORY_HOSPITAL = "HOSPITAL";

    private final ElasticsearchOperations elasticsearchOperations;
    private final FacilityRepository facilityRepository;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final HiraSpecialtyConfig hiraSpecialtyConfig;
    private final SymptomSpecialtyMapping symptomSpecialtyMapping;

    /**
     * 진료과 코드 + 위치 기반 병원 검색.
     * textQuery가 있으면 자연어 쿼리(예: "강남 정형외과")로 ES+Kakao 검색.
     * 내부 ES → Kakao Local 결과 합산, openNow 필터 적용.
     */
    @Cacheable(
        value = "hospitalSearch",
        key = "#lat + ':' + #lng + ':' + #radiusKm + ':' + #specialtyCodes + ':' + #openNow",
        unless = "#result.content.isEmpty()"
    )
    public Page<HospitalSearchResult> searchHospitals(
            double lat, double lng, double radiusKm,
            List<String> specialtyCodes,
            String textQuery,
            Boolean openNow,
            Pageable pageable) {

        boolean hasText = textQuery != null && !textQuery.isBlank();

        // 1. ES에서 내부 병원 조회
        List<HospitalSearchResult> internalResults = hasText
                ? searchInternalByText(lat, lng, radiusKm, textQuery)
                : searchInternal(lat, lng, radiusKm, specialtyCodes);

        // 2. openNow 필터
        if (Boolean.TRUE.equals(openNow)) {
            internalResults = internalResults.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getOpenNow()))
                    .collect(Collectors.toList());
        }

        // 3. Kakao Local 검색
        // textQuery가 있으면 그 텍스트 자체를 Kakao 키워드로 사용 (지역명+진료과 포함)
        List<HospitalSearchResult> kakaoResults = hasText
                ? searchKakaoByText(lat, lng, (int)(radiusKm * 1000), textQuery)
                : searchKakao(lat, lng, (int)(radiusKm * 1000), specialtyCodes);

        // 4. placeId 기준 중복 제거 (내부 우선)
        Set<String> internalPlaceIds = internalResults.stream()
                .map(HospitalSearchResult::getPlaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<HospitalSearchResult> deduped = kakaoResults.stream()
                .filter(r -> !internalPlaceIds.contains(r.getPlaceId()))
                .collect(Collectors.toList());

        // 5. 합산 + 정렬: 내부 영업중 → 내부 기타 → Kakao
        List<HospitalSearchResult> merged = new ArrayList<>();
        merged.addAll(internalResults);
        merged.addAll(deduped);

        AtomicInteger rank = new AtomicInteger(1);
        List<HospitalSearchResult> ranked = merged.stream()
                .map(r -> HospitalSearchResult.builder()
                        .facilityId(r.getFacilityId())
                        .placeId(r.getPlaceId())
                        .name(r.getName())
                        .address(r.getAddress())
                        .latitude(r.getLatitude())
                        .longitude(r.getLongitude())
                        .specialties(r.getSpecialties())
                        .openNow(r.getOpenNow())
                        .operatingHours(r.getOperatingHours())
                        .source(r.getSource())
                        .rank(rank.getAndIncrement())
                        .build())
                .collect(Collectors.toList());

        // 페이지 처리
        int total = ranked.size();
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), total);
        List<HospitalSearchResult> pageContent = from >= total ? List.of() : ranked.subList(from, to);

        return new PageImpl<>(pageContent, pageable, total);
    }

    /**
     * 자연어 쿼리로 ES 내부 병원 검색.
     * nori 형태소 분석 match + geo_distance + category=HOSPITAL
     */
    private List<HospitalSearchResult> searchInternalByText(
            double lat, double lng, double radiusKm, String textQuery) {
        try {
            Query geoQuery = Query.of(q -> q
                    .geoDistance(gd -> gd
                            .field("location")
                            .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                            .distance(radiusKm + "km")));

            Query nameMatch = Query.of(q -> q
                    .match(m -> m.field("name").query(textQuery).fuzziness("AUTO")));

            Query finalQuery = Query.of(q -> q
                    .bool(b -> b
                            .must(nameMatch)
                            .filter(geoQuery)
                            .filter(f -> f.term(t -> t.field("category").value(CATEGORY_HOSPITAL)))));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(finalQuery)
                    .withPageable(PageRequest.of(0, 50))
                    .build();

            SearchHits<FacilitySearchDocument> hits = elasticsearchOperations
                    .search(nativeQuery, FacilitySearchDocument.class);

            List<FacilitySearchDocument> docs = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
            Map<String, Facility> facilityMap = batchLoadFacilities(docs);
            return docs.stream()
                    .map(doc -> buildFromDoc(doc, facilityMap))
                    .sorted(Comparator.comparing(r -> Boolean.TRUE.equals(r.getOpenNow()) ? 0 : 1))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[HospitalSearch] ES 텍스트 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 자연어 쿼리를 Kakao Local HP8 카테고리로 직접 검색.
     * "강남 정형외과" → Kakao가 지역+업종 조합으로 처리.
     */
    private List<HospitalSearchResult> searchKakaoByText(
            double lat, double lng, int radiusMeters, String textQuery) {
        List<PlaceSearchResult> kakaoPlaces = kakaoLocalSearchClient.searchHospitalsBySpecialty(
                textQuery, lat, lng, radiusMeters);

        return kakaoPlaces.stream()
                .map(place -> HospitalSearchResult.builder()
                        .facilityId(null)
                        .placeId(place.getPlaceId())
                        .name(place.getName())
                        .address(place.getAddress())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .specialties(List.of())
                        .openNow(null)
                        .operatingHours(null)
                        .source("KAKAO")
                        .rank(0)
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Facility> batchLoadFacilities(List<FacilitySearchDocument> docs) {
        List<String> ids = docs.stream().map(FacilitySearchDocument::getId).collect(Collectors.toList());
        return facilityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Facility::getId, f -> f));
    }

    private HospitalSearchResult buildFromDoc(FacilitySearchDocument doc, Map<String, Facility> facilityMap) {
        Facility facility = facilityMap.get(doc.getId());
        Boolean isOpen = facility != null ? isOpenNow(facility) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> opHours = facility != null
                ? (Map<String, Object>) facility.getMetadata().get("operatingHours")
                : null;
        return HospitalSearchResult.builder()
                .facilityId(doc.getId())
                .placeId(doc.getPlaceId())
                .name(doc.getName())
                .address(doc.getAddress())
                .latitude(doc.getLocation() != null ? doc.getLocation().getLat() : null)
                .longitude(doc.getLocation() != null ? doc.getLocation().getLon() : null)
                .specialties(doc.getSpecialties())
                .openNow(isOpen)
                .operatingHours(opHours)
                .source("INTERNAL")
                .rank(0)
                .build();
    }

    private List<HospitalSearchResult> searchInternal(
            double lat, double lng, double radiusKm, List<String> specialtyCodes) {
        try {
            Query geoQuery = Query.of(q -> q
                    .geoDistance(gd -> gd
                            .field("location")
                            .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                            .distance(radiusKm + "km")));

            Query finalQuery;
            if (specialtyCodes != null && !specialtyCodes.isEmpty()) {
                List<co.elastic.clients.elasticsearch._types.FieldValue> fieldValues = specialtyCodes.stream()
                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                        .collect(Collectors.toList());
                finalQuery = Query.of(q -> q
                        .bool(b -> b
                                .must(geoQuery)
                                .filter(f -> f.terms(t -> t.field("specialties").terms(tv -> tv.value(fieldValues))))
                                .filter(f2 -> f2.term(t -> t.field("category").value(CATEGORY_HOSPITAL)))));
            } else {
                finalQuery = Query.of(q -> q
                        .bool(b -> b
                                .must(geoQuery)
                                .filter(f -> f.term(t -> t.field("category").value(CATEGORY_HOSPITAL)))));
            }

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(finalQuery)
                    .withPageable(PageRequest.of(0, 50))
                    .build();

            SearchHits<FacilitySearchDocument> hits = elasticsearchOperations
                    .search(nativeQuery, FacilitySearchDocument.class);

            List<FacilitySearchDocument> docs = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
            Map<String, Facility> facilityMap = batchLoadFacilities(docs);
            return docs.stream()
                    .map(doc -> buildFromDoc(doc, facilityMap))
                    .sorted(Comparator.comparing(r -> Boolean.TRUE.equals(r.getOpenNow()) ? 0 : 1))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[HospitalSearch] ES 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<HospitalSearchResult> searchKakao(
            double lat, double lng, int radiusMeters, List<String> specialtyCodes) {
        if (specialtyCodes == null || specialtyCodes.isEmpty()) {
            return List.of();
        }
        // 코드별로 Kakao 검색 후 합산
        Set<String> seenPlaceIds = new LinkedHashSet<>();
        List<HospitalSearchResult> results = new ArrayList<>();

        for (String code : specialtyCodes) {
            String keyword = hiraSpecialtyConfig.getKakaoKeyword(code);
            if (keyword == null) continue;

            List<PlaceSearchResult> kakaoPlaces = kakaoLocalSearchClient.searchHospitalsBySpecialty(
                    keyword, lat, lng, radiusMeters);

            for (PlaceSearchResult place : kakaoPlaces) {
                if (seenPlaceIds.add(place.getPlaceId())) {
                    results.add(HospitalSearchResult.builder()
                            .facilityId(null)
                            .placeId(place.getPlaceId())
                            .name(place.getName())
                            .address(place.getAddress())
                            .latitude(place.getLatitude())
                            .longitude(place.getLongitude())
                            .specialties(List.of(code))
                            .openNow(null)
                            .operatingHours(null)
                            .source("KAKAO")
                            .rank(0)
                            .build());
                }
            }
        }
        return results;
    }

    /**
     * KST 현재 시각 기준으로 운영 중인지 판단.
     * metadata.operatingHours = {"MON": {"open":"09:00","close":"18:00"}, ...}
     */
    @SuppressWarnings("unchecked")
    private Boolean isOpenNow(Facility facility) {
        Object raw = facility.getMetadata().get("operatingHours");
        if (raw == null) return null;
        try {
            Map<String, Object> opHours = (Map<String, Object>) raw;
            ZonedDateTime now = ZonedDateTime.now(KST);
            String dayKey = now.getDayOfWeek().name().substring(0, 3); // MON, TUE, ...
            Object dayRaw = opHours.get(dayKey);
            if (dayRaw == null) return false; // 해당 요일 운영 없음
            Map<String, String> dayInfo = (Map<String, String>) dayRaw;
            String openStr = dayInfo.get("open");
            String closeStr = dayInfo.get("close");
            if (openStr == null || closeStr == null) return null;
            LocalTime open = LocalTime.parse(openStr);
            LocalTime close = LocalTime.parse(closeStr);
            LocalTime current = now.toLocalTime();
            return !current.isBefore(open) && current.isBefore(close);
        } catch (Exception e) {
            log.debug("[HospitalSearch] 운영시간 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
