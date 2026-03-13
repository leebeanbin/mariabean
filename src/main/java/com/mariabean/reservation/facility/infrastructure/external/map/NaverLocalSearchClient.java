package com.mariabean.reservation.facility.infrastructure.external.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NaverLocalSearchClient {

    private static final String LOCAL_URL = "https://openapi.naver.com/v1/search/local.json";

    @Value("${naver.search.client-id:}")
    private String clientId;

    @Value("${naver.search.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public NaverLocalSearchClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public List<PlaceSearchResult> searchPlaces(String query) {
        if (!isConfigured()) return List.of();

        String url = UriComponentsBuilder.fromHttpUrl(LOCAL_URL)
                .queryParam("query", query)
                .queryParam("display", 10)
                .queryParam("sort", "random")
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode items = response.getBody() != null ? response.getBody().path("items") : null;
            if (items == null || !items.isArray()) return List.of();

            List<PlaceSearchResult> results = new ArrayList<>();
            int idx = 0;
            for (JsonNode item : items) {
                String title = stripTags(item.path("title").asText(""));
                String address = item.path("roadAddress").asText("");
                if (address.isBlank()) address = item.path("address").asText("");
                Double lng = parseNullableDouble(item.path("mapx").asText(null));
                Double lat = parseNullableDouble(item.path("mapy").asText(null));
                if (address.isBlank() || lat == null || lng == null) continue;

                // mapx/mapy are TM128-like for this API; do not trust as final coordinates.
                // We keep entries for recommendation list and rely on geocoding fallback when needed.
                results.add(PlaceSearchResult.builder()
                        .placeId("naver-local-" + idx++)
                        .name(title.isBlank() ? address : title)
                        .address(address)
                        .latitude(lat)
                        .longitude(lng)
                        .provider("NAVER_LOCAL")
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.warn("[NaverLocal] search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private String stripTags(String value) {
        return value == null ? "" : value.replaceAll("<[^>]*>", "");
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
