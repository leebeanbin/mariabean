package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacePhotoEnrichmentService {

    private final ObjectMapper objectMapper;

    @Value("${google.maps.api-key:}")
    private String googleApiKey;

    @Value("${app.search.photo-max-count:4}")
    private int photoMaxCount;

    /**
     * 사진 enrichment: Kakao Place → Google Places → Tavily 웹 이미지 순으로 시도
     */
    public List<String> fetchPhotos(String placeId, String googlePlaceId, List<String> webImages) {
        List<String> photos = new ArrayList<>();

        // 1. Kakao Place Detail 사진
        if (placeId != null && !placeId.isBlank()) {
            photos.addAll(fetchKakaoPhotos(placeId));
        }

        // 2. Google Places 사진
        if (photos.size() < 2 && googlePlaceId != null && !googlePlaceId.isBlank()) {
            photos.addAll(fetchGooglePhotos(googlePlaceId));
        }

        // 3. Tavily 웹 이미지 보완
        if (photos.size() < 2 && webImages != null) {
            int needed = 2 - photos.size();
            webImages.stream()
                    .filter(url -> url != null && url.startsWith("https://"))
                    .limit(needed)
                    .forEach(photos::add);
        }

        return photos.stream().distinct().limit(photoMaxCount).toList();
    }

    private List<String> fetchKakaoPhotos(String placeId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://place.map.kakao.com/m/main/v/" + placeId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode imageList = root.path("place").path("basicInfo").path("mainphotourl");
            List<String> photos = new ArrayList<>();
            if (!imageList.isMissingNode() && imageList.isTextual()) {
                photos.add(imageList.asText());
            }
            // 추가 리뷰 이미지
            JsonNode reviewImages = root.path("reviewImageList");
            if (reviewImages.isArray()) {
                StreamSupport.stream(reviewImages.spliterator(), false)
                        .map(n -> n.path("imageUrl").asText(""))
                        .filter(u -> u.startsWith("https://"))
                        .limit(2)
                        .forEach(photos::add);
            }
            return photos;
        } catch (Exception e) {
            log.debug("[Photo] Kakao 사진 로드 실패 placeId={}: {}", placeId, e.getMessage());
            return List.of();
        }
    }

    private List<String> fetchGooglePhotos(String googlePlaceId) {
        if (googleApiKey == null || googleApiKey.isBlank()) return List.of();
        try {
            RestTemplate restTemplate = new RestTemplate();
            String detailUrl = "https://maps.googleapis.com/maps/api/place/details/json"
                    + "?place_id=" + googlePlaceId
                    + "&fields=photos"
                    + "&key=" + googleApiKey;

            ResponseEntity<String> response = restTemplate.getForEntity(detailUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode photos = root.path("result").path("photos");
            List<String> urls = new ArrayList<>();
            if (photos.isArray()) {
                for (JsonNode photo : photos) {
                    String ref = photo.path("photo_reference").asText("");
                    if (!ref.isBlank()) {
                        urls.add("https://maps.googleapis.com/maps/api/place/photo"
                                + "?maxwidth=400&photo_reference=" + ref + "&key=" + googleApiKey);
                    }
                    if (urls.size() >= 2) break;
                }
            }
            return urls;
        } catch (Exception e) {
            log.debug("[Photo] Google 사진 로드 실패 placeId={}: {}", googlePlaceId, e.getMessage());
            return List.of();
        }
    }
}
