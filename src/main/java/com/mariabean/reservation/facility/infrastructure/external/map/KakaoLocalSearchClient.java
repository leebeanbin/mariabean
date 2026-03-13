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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KakaoLocalSearchClient {

    private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    @Value("${kakao.local.rest-api-key:}")
    private String restApiKey;

    private final RestTemplate restTemplate;

    public KakaoLocalSearchClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    public List<PlaceSearchResult> searchPlaces(String query) {
        if (!isConfigured()) {
            log.debug("[KakaoLocal] REST API key not configured. Returning empty results.");
            return List.of();
        }

        String keywordUrl = UriComponentsBuilder.fromHttpUrl(KEYWORD_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("size", 10)
                .toUriString();
        String addressUrl = UriComponentsBuilder.fromHttpUrl(ADDRESS_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("size", 10)
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + restApiKey);
            ResponseEntity<JsonNode> keywordResponse = restTemplate.exchange(
                    keywordUrl, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            ResponseEntity<JsonNode> addressResponse = restTemplate.exchange(
                    addressUrl, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

            List<PlaceSearchResult> keywordResults = parseKeywordDocuments(
                    keywordResponse.getBody() != null ? keywordResponse.getBody().path("documents") : null
            );
            List<PlaceSearchResult> addressResults = parseAddressDocuments(
                    addressResponse.getBody() != null ? addressResponse.getBody().path("documents") : null
            );

            List<PlaceSearchResult> merged = new ArrayList<>();
            merged.addAll(addressResults); // 주소형 먼저
            merged.addAll(keywordResults);
            return deduplicate(merged, 12);
        } catch (Exception e) {
            log.warn("[KakaoLocal] search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<PlaceSearchResult> parseKeywordDocuments(JsonNode docs) {
        if (docs == null || !docs.isArray()) return List.of();
        List<PlaceSearchResult> results = new ArrayList<>();
        for (JsonNode item : docs) {
            String placeId = item.path("id").asText(null);
            String name = item.path("place_name").asText("");
            String roadAddress = item.path("road_address_name").asText("");
            String jibunAddress = item.path("address_name").asText("");
            String address = !roadAddress.isBlank() ? roadAddress : jibunAddress;
            Double lng = parseDouble(item.path("x").asText(null));
            Double lat = parseDouble(item.path("y").asText(null));
            if (address.isBlank() || lat == null || lng == null) continue;
            results.add(PlaceSearchResult.builder()
                    .placeId(placeId != null ? "kakao-" + placeId : "kakao-keyword-" + Math.abs((name + address).hashCode()))
                    .name(name.isBlank() ? address : name)
                    .address(address)
                    .latitude(lat)
                    .longitude(lng)
                    .provider("KAKAO")
                    .matchType(isAddressLike(name, address) ? "ADDRESS" : "PLACE")
                    .build());
        }
        return results;
    }

    private List<PlaceSearchResult> parseAddressDocuments(JsonNode docs) {
        if (docs == null || !docs.isArray()) return List.of();
        List<PlaceSearchResult> results = new ArrayList<>();
        for (JsonNode item : docs) {
            String roadAddress = item.path("road_address").path("address_name").asText("");
            String jibunAddress = item.path("address").path("address_name").asText("");
            String address = !roadAddress.isBlank() ? roadAddress : jibunAddress;
            Double lng = parseDouble(item.path("x").asText(null));
            Double lat = parseDouble(item.path("y").asText(null));
            if (address.isBlank() || lat == null || lng == null) continue;
            results.add(PlaceSearchResult.builder()
                    .placeId("kakao-address-" + Math.abs(address.hashCode()))
                    .name(address)
                    .address(address)
                    .latitude(lat)
                    .longitude(lng)
                    .provider("KAKAO")
                    .matchType("ADDRESS")
                    .build());
        }
        return results;
    }

    private List<PlaceSearchResult> deduplicate(List<PlaceSearchResult> input, int limit) {
        List<PlaceSearchResult> output = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (PlaceSearchResult item : input) {
            if (item == null) continue;
            String key = ((item.getName() == null ? "" : item.getName().trim().toLowerCase()) + "|"
                    + (item.getAddress() == null ? "" : item.getAddress().trim().toLowerCase()));
            if (seen.add(key)) {
                output.add(item);
            }
            if (output.size() >= limit) break;
        }
        return output;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 진료과명 + "병원" 키워드로 Kakao Local 검색 (category_group_code=HP8).
     * @param specialtyKoreanName 예: "내과", "산부인과"
     * @param lat 중심 위도
     * @param lng 중심 경도
     * @param radiusMeters 반경 (최대 20000)
     */
    public List<PlaceSearchResult> searchHospitalsBySpecialty(
            String specialtyKoreanName, double lat, double lng, int radiusMeters) {
        if (!isConfigured()) {
            log.debug("[KakaoLocal] REST API key not configured.");
            return List.of();
        }
        String url = UriComponentsBuilder.fromHttpUrl(KEYWORD_SEARCH_URL)
                .queryParam("query", specialtyKoreanName + " 병원")
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", Math.min(radiusMeters, 20000))
                .queryParam("category_group_code", "HP8")
                .queryParam("size", 15)
                .toUriString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + restApiKey);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode docs = response.getBody() != null ? response.getBody().path("documents") : null;
            return parseKeywordDocuments(docs);
        } catch (Exception e) {
            log.warn("[KakaoLocal] hospital search failed specialty='{}': {}", specialtyKoreanName, e.getMessage());
            return List.of();
        }
    }

    private boolean isAddressLike(String name, String address) {
        String target = ((name == null ? "" : name) + " " + (address == null ? "" : address)).toLowerCase();
        return target.matches(".*\\d+.*")
                || target.contains("로")
                || target.contains("길")
                || target.contains("동")
                || target.contains("읍")
                || target.contains("면");
    }
}

