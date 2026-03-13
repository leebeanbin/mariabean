package com.mariabean.reservation.facility.infrastructure.external.map;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class KakaoMobilityClient {

    private static final String DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions";
    private static final String MULTI_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions";

    @Value("${kakao.local.rest-api-key:}")
    private String restApiKey;

    private final RestTemplate restTemplate;

    public KakaoMobilityClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    /**
     * 자동차 경로 탐색.
     * @param originLng   출발지 경도
     * @param originLat   출발지 위도
     * @param destLng     목적지 경도
     * @param destLat     목적지 위도
     * @return RouteResult or null
     */
    public RouteResult getCarRoute(double originLng, double originLat, double destLng, double destLat) {
        if (!isConfigured()) return null;
        String url = UriComponentsBuilder.fromHttpUrl(DIRECTIONS_URL)
                .queryParam("origin", originLng + "," + originLat)
                .queryParam("destination", destLng + "," + destLat)
                .queryParam("priority", "RECOMMEND")
                .toUriString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + restApiKey);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            return parseRoute(resp.getBody(), "car");
        } catch (Exception e) {
            log.warn("[KakaoMobility] car route failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 도보 경로 탐색.
     */
    public RouteResult getWalkRoute(double originLng, double originLat, double destLng, double destLat) {
        if (!isConfigured()) return null;
        String url = UriComponentsBuilder.fromHttpUrl("https://apis-navi.kakaomobility.com/v1/walking/directions")
                .queryParam("origin", originLng + "," + originLat)
                .queryParam("destination", destLng + "," + destLat)
                .toUriString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + restApiKey);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            return parseRoute(resp.getBody(), "walk");
        } catch (Exception e) {
            log.warn("[KakaoMobility] walk route failed: {}", e.getMessage());
            return null;
        }
    }

    private RouteResult parseRoute(JsonNode body, String mode) {
        if (body == null) return null;
        JsonNode routes = body.path("routes");
        if (!routes.isArray() || routes.isEmpty()) return null;
        JsonNode route = routes.get(0);
        JsonNode summary = route.path("summary");
        int durationSec = summary.path("duration").asInt(0);
        int distanceM = summary.path("distance").asInt(0);
        String taxiFare = summary.path("fare").path("taxi").asText("0");
        String tollFare = summary.path("fare").path("toll").asText("0");
        return new RouteResult(mode, durationSec, distanceM, Integer.parseInt(taxiFare), Integer.parseInt(tollFare));
    }

    public record RouteResult(String mode, int durationSeconds, int distanceMeters, int taxiFare, int tollFare) {
        public String formatDuration() {
            int hours = durationSeconds / 3600;
            int minutes = (durationSeconds % 3600) / 60;
            if (hours > 0) return hours + "시간 " + minutes + "분";
            return minutes + "분";
        }
        public String formatDistance() {
            if (distanceMeters >= 1000) return String.format("%.1fkm", distanceMeters / 1000.0);
            return distanceMeters + "m";
        }
    }
}
