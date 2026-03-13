package com.mariabean.reservation.facility.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.facility.application.analytics.MapAnalyticsQueryService;
import com.mariabean.reservation.facility.application.analytics.MapSearchEvent;
import com.mariabean.reservation.facility.application.analytics.MapSearchEventPublisher;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.infrastructure.external.map.GooglePlacesClient;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.facility.infrastructure.external.map.KakaoLocalSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Map provider 연동 오케스트레이션 서비스.
 * 내부 시설 우선 + Kakao/Google 보완 검색을 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {
    private static final String PROVIDER_ORDER = "INTERNAL>KAKAO>GOOGLE";
    private static final Duration SEARCH_CACHE_TTL = Duration.ofSeconds(90);
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration SEARCH_LOCK_TTL = Duration.ofSeconds(2);
    private static final String SEARCH_CACHE_PREFIX = "map:search:v1:";
    private static final String SEARCH_LOCK_PREFIX = "map:search:lock:v1:";
    private static final String DETAIL_CACHE_PREFIX = "map:detail:v1:";

    private final FacilityRepository facilityRepository;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final GooglePlacesClient googlePlacesClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MapSearchEventPublisher mapSearchEventPublisher;
    private final MapAnalyticsQueryService mapAnalyticsQueryService;

    /**
     * 장소 후보 목록 검색 (프론트엔드 자동완성용).
     * 우선순위: INTERNAL -> KAKAO -> GOOGLE
     */
    public List<PlaceSearchResult> searchPlaces(String query) {
        long startedAt = System.currentTimeMillis();
        String normalized = normalize(query);
        if (normalized.isBlank()) return List.of();
        String queryType = inferQueryType(normalized);

        String cacheKey = SEARCH_CACHE_PREFIX + normalized;
        List<PlaceSearchResult> cached = getCachedList(cacheKey);
        if (!cached.isEmpty()) {
            publishSearchEvent(normalized, queryType, topMatchType(cached), cached.size(), true, false, "CACHE", false, startedAt);
            return cached;
        }

        String lockKey = SEARCH_LOCK_PREFIX + normalized;
        Boolean locked = false;
        try {
            locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", SEARCH_LOCK_TTL);
        } catch (Exception e) {
            log.debug("[MapService] lock acquisition skipped: {}", e.getMessage());
        }

        if (!Boolean.TRUE.equals(locked)) {
            List<PlaceSearchResult> warm = waitForWarmCache(cacheKey);
            if (!warm.isEmpty()) {
                publishSearchEvent(normalized, queryType, topMatchType(warm), warm.size(), true, false, "CACHE_WAIT", false, startedAt);
                return warm;
            }
        }

        List<PlaceSearchResult> merged = new ArrayList<>();
        boolean fallbackUsed = false;
        try {
            Optional<Facility> internalById = facilityRepository.findByPlaceId(normalized);
            internalById.ifPresent(thisFacility -> merged.add(toInternal(thisFacility)));

            List<PlaceSearchResult> kakao = kakaoLocalSearchClient.searchPlaces(query);
            merged.addAll(kakao);
            if (kakao.isEmpty()) {
                fallbackUsed = true;
            }
            List<PlaceSearchResult> google = googlePlacesClient.searchPlaces(query);
            merged.addAll(google);

            List<PlaceSearchResult> deduped = deduplicate(merged);
            List<PlaceSearchResult> ranked = rankResults(deduped, normalized);
            String topMatchType = topMatchType(ranked);
            cacheList(cacheKey, ranked, SEARCH_CACHE_TTL);
            String providerUsed;
            if (!kakao.isEmpty()) providerUsed = "KAKAO";
            else if (!google.isEmpty()) providerUsed = "GOOGLE";
            else providerUsed = "NONE";
            publishSearchEvent(normalized, queryType, topMatchType, ranked.size(), false, fallbackUsed, providerUsed, false, startedAt);
            if (!mapSearchEventPublisher.isEnabled()) {
                // Kafka 미사용 환경에서는 Redis 집계를 동기 fallback으로 유지한다.
                mapAnalyticsQueryService.incrementPopularKeyword(normalized);
            }
            return ranked;
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                try {
                    redisTemplate.delete(lockKey);
                } catch (Exception ignore) {
                    // lock best effort
                }
            }
        }
    }

    /**
     * placeId로 상세 정보 조회.
     * 내부 시설 우선 조회 + 외부 상세 보완.
     */
    public PlaceSearchResult getPlaceDetails(String placeId) {
        String normalized = normalize(placeId);
        if (normalized.isBlank()) return null;

        String cacheKey = DETAIL_CACHE_PREFIX + normalized;
        PlaceSearchResult cached = getCachedDetail(cacheKey);
        if (cached != null) return cached;

        var internal = resolveInternalFacility(placeId);
        if (internal.isPresent()) {
            var f = internal.get();
            PlaceSearchResult internalResult = PlaceSearchResult.builder()
                    .placeId(f.getPlaceId() != null ? f.getPlaceId() : "facility-" + f.getId())
                    .name(f.getName())
                    .address(f.getAddress())
                    .latitude(f.getLatitude())
                    .longitude(f.getLongitude())
                    .provider("INTERNAL")
                    .sourceFacilityId(f.getId())
                    .matchType("INTERNAL")
                    .build();
            cacheDetail(cacheKey, internalResult, DETAIL_CACHE_TTL);
            return internalResult;
        }
        PlaceSearchResult external = googlePlacesClient.getPlaceDetails(placeId);
        if (external != null) {
            cacheDetail(cacheKey, external, DETAIL_CACHE_TTL);
        }
        return external;
    }

    private java.util.Optional<com.mariabean.reservation.facility.domain.Facility> resolveInternalFacility(String placeId) {
        if (placeId == null || placeId.isBlank()) return java.util.Optional.empty();
        if (placeId.startsWith("facility-")) {
            return facilityRepository.findById(placeId.substring("facility-".length()));
        }
        return facilityRepository.findByPlaceId(placeId).or(() -> facilityRepository.findById(placeId));
    }

    private PlaceSearchResult toInternal(Facility facility) {
        return PlaceSearchResult.builder()
                .placeId(facility.getPlaceId() != null ? facility.getPlaceId() : "facility-" + facility.getId())
                .name(facility.getName())
                .address(facility.getAddress())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .provider("INTERNAL")
                .sourceFacilityId(facility.getId())
                .matchType("INTERNAL")
                .build();
    }

    private List<PlaceSearchResult> deduplicate(List<PlaceSearchResult> input) {
        Set<String> seen = new LinkedHashSet<>();
        List<PlaceSearchResult> result = new ArrayList<>();
        for (PlaceSearchResult item : input) {
            if (item == null) continue;
            PlaceSearchResult normalizedItem = annotateMatchType(item);
            String key = normalize(item.getName()) + "|" + normalize(item.getAddress());
            if (seen.add(key)) {
                result.add(normalizedItem);
            }
            if (result.size() >= 12) break;
        }
        return result;
    }

    private List<PlaceSearchResult> rankResults(List<PlaceSearchResult> input, String normalizedQuery) {
        List<PlaceSearchResult> ranked = new ArrayList<>(input);
        ranked.sort(
                Comparator.comparingInt((PlaceSearchResult item) -> matchTypePriority(item.getMatchType()))
                        .thenComparingInt(item -> -querySimilarityScore(item, normalizedQuery))
        );
        return ranked;
    }

    private List<PlaceSearchResult> waitForWarmCache(String cacheKey) {
        for (int i = 0; i < 3; i++) {
            List<PlaceSearchResult> cached = getCachedList(cacheKey);
            if (!cached.isEmpty()) return cached;
            try {
                TimeUnit.MILLISECONDS.sleep(40L * (i + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }
        return List.of();
    }

    private int matchTypePriority(String matchType) {
        if ("INTERNAL".equalsIgnoreCase(matchType)) return 0;
        if ("ADDRESS".equalsIgnoreCase(matchType)) return 1;
        return 2;
    }

    private int querySimilarityScore(PlaceSearchResult item, String normalizedQuery) {
        String name = normalize(item.getName());
        String address = normalize(item.getAddress());
        if (address.equals(normalizedQuery) || name.equals(normalizedQuery)) return 100;
        if (address.startsWith(normalizedQuery) || name.startsWith(normalizedQuery)) return 80;
        if (address.contains(normalizedQuery)) return 60;
        if (name.contains(normalizedQuery)) return 40;
        return 0;
    }

    private PlaceSearchResult annotateMatchType(PlaceSearchResult item) {
        if (item == null) return null;
        if ("INTERNAL".equalsIgnoreCase(item.getProvider()) || item.getSourceFacilityId() != null) {
            return toBuilder(item).matchType("INTERNAL").build();
        }
        if (isAddressLike(item.getAddress()) || isAddressLike(item.getName())) {
            return toBuilder(item).matchType("ADDRESS").build();
        }
        return toBuilder(item).matchType("PLACE").build();
    }

    private PlaceSearchResult.PlaceSearchResultBuilder toBuilder(PlaceSearchResult item) {
        return PlaceSearchResult.builder()
                .placeId(item.getPlaceId())
                .name(item.getName())
                .address(item.getAddress())
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .provider(item.getProvider())
                .sourceFacilityId(item.getSourceFacilityId())
                .matchType(item.getMatchType());
    }

    private boolean isAddressLike(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return false;
        return normalized.matches(".*\\d+.*")
                || normalized.contains("도로")
                || normalized.contains("로")
                || normalized.contains("길")
                || normalized.contains("번길")
                || normalized.contains("번지")
                || normalized.contains("동")
                || normalized.contains("읍")
                || normalized.contains("면")
                || normalized.contains("리");
    }

    private String inferQueryType(String query) {
        return isAddressLike(query) ? "address_like" : "place_like";
    }

    public void recordSuggestionClick(String query, String queryType, String matchType) {
        String normalized = normalize(query);
        if (normalized.isBlank()) return;
        long startedAt = System.currentTimeMillis();
        publishSearchEvent(
                normalized,
                (queryType == null || queryType.isBlank()) ? inferQueryType(normalized) : queryType.toLowerCase(),
                (matchType == null || matchType.isBlank()) ? "UNKNOWN" : matchType.toUpperCase(),
                0,
                false,
                false,
                "CLIENT",
                true,
                startedAt
        );
    }

    private String topMatchType(List<PlaceSearchResult> results) {
        if (results == null || results.isEmpty()) return "NONE";
        String top = results.get(0).getMatchType();
        return (top == null || top.isBlank()) ? "UNKNOWN" : top.toUpperCase();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void publishSearchEvent(
            String query,
            String queryType,
            String topMatchType,
            int resultCount,
            boolean cacheHit,
            boolean fallbackUsed,
            String providerUsed,
            boolean suggestionClicked,
            long startedAt
    ) {
        mapSearchEventPublisher.publish(MapSearchEvent.builder()
                .query(query)
                .providerOrder(PROVIDER_ORDER)
                .providerUsed(providerUsed)
                .viewportHash("none")
                .hitStatus(cacheHit ? "HIT" : "MISS")
                .queryType(queryType)
                .topMatchType(topMatchType)
                .suggestionClicked(suggestionClicked)
                .resultCount(resultCount)
                .cacheHit(cacheHit)
                .fallbackUsed(fallbackUsed)
                .elapsedMs(System.currentTimeMillis() - startedAt)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private List<PlaceSearchResult> getCachedList(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) return List.of();
            return objectMapper.readValue(raw, new TypeReference<List<PlaceSearchResult>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void cacheList(String key, List<PlaceSearchResult> data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            log.debug("[MapService] cacheList skipped: {}", e.getMessage());
        }
    }

    private PlaceSearchResult getCachedDetail(String key) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) return null;
            return objectMapper.readValue(raw, PlaceSearchResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheDetail(String key, PlaceSearchResult data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            log.debug("[MapService] cacheDetail skipped: {}", e.getMessage());
        }
    }
}
