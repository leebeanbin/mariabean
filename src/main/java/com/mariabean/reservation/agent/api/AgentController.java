package com.mariabean.reservation.agent.api;

import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.facility.infrastructure.external.map.KakaoLocalSearchClient;
import com.mariabean.reservation.facility.infrastructure.external.map.KakaoMobilityClient;
import com.mariabean.reservation.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/agent")
@RequiredArgsConstructor
public class AgentController {

    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final KakaoMobilityClient kakaoMobilityClient;

    /**
     * 주변 장소 검색.
     * GET /api/v1/public/agent/nearby?lat=37.5&lng=127.0&query=카페&radius=1000
     */
    @GetMapping("/nearby")
    public CommonResponse<List<PlaceSearchResult>> searchNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "1000") int radius,
            @RequestParam(required = false, defaultValue = "") String category) {

        String searchQuery = query.isBlank() ? buildCategoryQuery(category) : query;
        if (searchQuery.isBlank()) searchQuery = "근처 시설";

        List<PlaceSearchResult> results = kakaoLocalSearchClient.searchNearby(searchQuery, lat, lng, radius);
        return CommonResponse.success(results);
    }

    /**
     * 경로 탐색.
     * GET /api/v1/public/agent/route?originLat=&originLng=&destLat=&destLng=&mode=car
     */
    @GetMapping("/route")
    public CommonResponse<Map<String, Object>> getRoute(
            @RequestParam double originLat,
            @RequestParam double originLng,
            @RequestParam double destLat,
            @RequestParam double destLng,
            @RequestParam(required = false, defaultValue = "car") String mode) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originLat", originLat);
        result.put("originLng", originLng);
        result.put("destLat", destLat);
        result.put("destLng", destLng);
        result.put("mode", mode);

        KakaoMobilityClient.RouteResult route = "walk".equals(mode)
                ? kakaoMobilityClient.getWalkRoute(originLng, originLat, destLng, destLat)
                : kakaoMobilityClient.getCarRoute(originLng, originLat, destLng, destLat);

        if (route != null) {
            result.put("duration", route.formatDuration());
            result.put("durationSeconds", route.durationSeconds());
            result.put("distance", route.formatDistance());
            result.put("distanceMeters", route.distanceMeters());
            result.put("taxiFare", route.taxiFare());
            result.put("tollFare", route.tollFare());
        } else {
            // fallback: haversine distance estimate
            double distKm = haversineKm(originLat, originLng, destLat, destLng);
            int estMinutes = "walk".equals(mode) ? (int)(distKm / 0.08) : (int)(distKm / 0.5);
            result.put("duration", estMinutes + "분 (예상)");
            result.put("durationSeconds", estMinutes * 60);
            result.put("distance", String.format("%.1fkm", distKm));
            result.put("distanceMeters", (int)(distKm * 1000));
            result.put("estimated", true);
        }
        return CommonResponse.success(result);
    }

    private String buildCategoryQuery(String category) {
        return switch (category.toUpperCase()) {
            case "HOSPITAL" -> "병원";
            case "CAFE" -> "카페";
            case "RESTAURANT" -> "식당";
            case "PHARMACY" -> "약국";
            case "CONVENIENCE" -> "편의점";
            case "PARKING" -> "주차장";
            default -> "";
        };
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng/2)*Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
