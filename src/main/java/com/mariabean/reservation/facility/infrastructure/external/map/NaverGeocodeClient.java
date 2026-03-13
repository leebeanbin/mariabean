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
public class NaverGeocodeClient {

    private static final String GEOCODE_URL = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode";

    @Value("${naver.maps.client-id:}")
    private String clientId;

    @Value("${naver.maps.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public NaverGeocodeClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public List<PlaceSearchResult> searchPlaces(String query) {
        if (!isConfigured()) {
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(GEOCODE_URL)
                .queryParam("query", query)
                .queryParam("count", 10)
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-ncp-apigw-api-key-id", clientId);
            headers.set("x-ncp-apigw-api-key", clientSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode root = response.getBody();
            JsonNode addresses = root != null ? root.path("addresses") : null;

            List<PlaceSearchResult> results = new ArrayList<>();
            if (addresses != null && addresses.isArray()) {
                int idx = 0;
                for (JsonNode item : addresses) {
                    String road = item.path("roadAddress").asText("");
                    String jibun = item.path("jibunAddress").asText("");
                    String address = !road.isBlank() ? road : jibun;
                    Double lng = parseNullableDouble(item.path("x").asText(null));
                    Double lat = parseNullableDouble(item.path("y").asText(null));
                    if (address.isBlank() || lat == null || lng == null) {
                        continue;
                    }

                    results.add(PlaceSearchResult.builder()
                            .placeId("naver-geocode-" + idx++)
                            .name(address)
                            .address(address)
                            .latitude(lat)
                            .longitude(lng)
                            .provider("NAVER_GEOCODE")
                            .build());
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[NaverGeocode] search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
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
