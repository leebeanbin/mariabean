package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TavilyWebSearchService {

    private final ObjectMapper objectMapper;

    @Value("${spring.ai.tavily.api-key}")
    private String tavilyApiKey;

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    public WebSearchResult search(String query, String locationHint) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String fullQuery = query + (locationHint != null && !locationHint.isBlank()
                    ? " " + locationHint : "") + " 위치 정보 리뷰";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", tavilyApiKey);
            body.put("query", fullQuery);
            body.put("max_results", 5);
            body.put("include_images", true);
            body.put("search_depth", "advanced");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(TAVILY_URL, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return WebSearchResult.empty();
            }

            return parseTavilyResponse(response.getBody());
        } catch (Exception e) {
            log.warn("[Tavily] 웹 검색 실패: {}", e.getMessage());
            return WebSearchResult.empty();
        }
    }

    private WebSearchResult parseTavilyResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<WebSearchResult.WebSnippet> snippets = new ArrayList<>();
            List<String> images = new ArrayList<>();

            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode r : results) {
                    snippets.add(WebSearchResult.WebSnippet.builder()
                            .title(r.path("title").asText(""))
                            .url(r.path("url").asText(""))
                            .content(truncate(r.path("content").asText(""), 200))
                            .score(r.path("score").asDouble(0.0))
                            .build());
                }
            }

            JsonNode imgs = root.path("images");
            if (imgs.isArray()) {
                for (JsonNode img : imgs) {
                    String url = img.asText("");
                    if (url.startsWith("https://")) {
                        images.add(url);
                    }
                }
            }

            return WebSearchResult.builder()
                    .snippets(snippets)
                    .imageUrls(images)
                    .build();
        } catch (Exception e) {
            log.warn("[Tavily] 응답 파싱 실패: {}", e.getMessage());
            return WebSearchResult.empty();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        // 문장 경계에서 truncate
        int cut = text.lastIndexOf('.', maxLen);
        return cut > 0 ? text.substring(0, cut + 1) : text.substring(0, maxLen);
    }
}
