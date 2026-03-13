package com.mariabean.reservation.facility.infrastructure.external.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Places API HTTP 클라이언트.
 *
 * 사용 API:
 *  - Text Search: https://maps.googleapis.com/maps/api/place/textsearch/json
 *  - Place Details: https://maps.googleapis.com/maps/api/place/details/json
 *
 * google.maps.api-key 환경변수가 없으면 빈 결과를 반환한다.
 */
@Slf4j
@Component
public class GooglePlacesClient {

    private static final String TEXT_SEARCH_URL =
            "https://maps.googleapis.com/maps/api/place/textsearch/json";
    private static final String DETAILS_URL =
            "https://maps.googleapis.com/maps/api/place/details/json";

    @Value("${google.maps.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public GooglePlacesClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 장소 이름으로 Google Places 텍스트 검색.
     * 프론트엔드에서 장소 후보 목록을 보여줄 때 사용.
     */
    public List<PlaceSearchResult> searchPlaces(String query) {
        if (!isConfigured()) {
            log.debug("[GooglePlaces] API key not configured. Returning empty results.");
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(TEXT_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("language", "ko")
                .queryParam("key", apiKey)
                .toUriString();

        try {
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            return parseResults(root != null ? root.path("results") : null);
        } catch (Exception e) {
            log.error("[GooglePlaces] Text search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * placeId로 장소 상세 정보 조회.
     * Facility 등록 시 이름/주소/좌표 자동 보완에 사용.
     */
    public PlaceSearchResult getPlaceDetails(String placeId) {
        if (!isConfigured()) {
            log.debug("[GooglePlaces] API key not configured. Cannot fetch place details.");
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(DETAILS_URL)
                .queryParam("place_id", placeId)
                .queryParam("fields", "place_id,name,formatted_address,geometry")
                .queryParam("language", "ko")
                .queryParam("key", apiKey)
                .toUriString();

        try {
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root == null || root.path("result").isMissingNode()) {
                return null;
            }
            return mapToResult(root.path("result"));
        } catch (Exception e) {
            log.error("[GooglePlaces] Details fetch failed for placeId='{}': {}", placeId, e.getMessage());
            return null;
        }
    }

    private List<PlaceSearchResult> parseResults(JsonNode resultsNode) {
        List<PlaceSearchResult> list = new ArrayList<>();
        if (resultsNode == null || !resultsNode.isArray()) return list;
        for (JsonNode node : resultsNode) {
            PlaceSearchResult result = mapToResult(node);
            if (result != null) list.add(result);
        }
        return list;
    }

    private PlaceSearchResult mapToResult(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode geo = node.path("geometry").path("location");
        return PlaceSearchResult.builder()
                .placeId(node.path("place_id").asText(null))
                .name(node.path("name").asText(null))
                .address(node.path("formatted_address").asText(null))
                .latitude(geo.isMissingNode() ? null : geo.path("lat").asDouble())
                .longitude(geo.isMissingNode() ? null : geo.path("lng").asDouble())
                .provider("GOOGLE")
                .matchType(isAddressLike(node.path("formatted_address").asText(null), node.path("name").asText(null)) ? "ADDRESS" : "PLACE")
                .build();
    }

    private boolean isAddressLike(String address, String name) {
        String target = ((address == null ? "" : address) + " " + (name == null ? "" : name)).toLowerCase();
        return target.matches(".*\\d+.*")
                || target.contains("road")
                || target.contains("street")
                || target.contains("avenue")
                || target.contains("ro")
                || target.contains("gil")
                || target.contains("dong")
                || target.contains("gu");
    }
}
