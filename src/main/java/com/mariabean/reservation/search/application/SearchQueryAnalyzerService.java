package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.SearchQueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryAnalyzerService {

    private final ChatClient chatClient;
    private final RedisTemplate<String, String> redisTemplate;

    public SearchQueryAnalysis analyze(String query, Long memberId) {
        try {
            String prompt = """
                    다음 검색어를 분석하세요. JSON 형식으로만 응답하세요.
                    {"keywords":["키워드1","키워드2"],"intentType":"hospital|facility|nearby|general","locationHint":"위치명 또는 빈문자열","needsWebSearch":true}

                    검색어: %s
                    """.formatted(query);

            String response = chatClient.prompt().user(prompt).call().content();
            SearchQueryAnalysis analysis = parseAnalysis(query, response);

            // Redis에 검색 이력 저장
            if (memberId != null) {
                String historyKey = "search:history:" + memberId;
                redisTemplate.opsForZSet().add(historyKey, query, System.currentTimeMillis());
                redisTemplate.expire(historyKey, Duration.ofDays(30));
            }

            return analysis;
        } catch (Exception e) {
            log.warn("[QueryAnalyzer] LLM 분석 실패, 키워드 fallback: {}", e.getMessage());
            return fallbackAnalysis(query);
        }
    }

    private SearchQueryAnalysis parseAnalysis(String query, String json) {
        try {
            // 간단한 JSON 파싱 (Jackson 없이 regex fallback)
            List<String> keywords = extractJsonArray(json, "keywords");
            String intentType = extractJsonString(json, "intentType", "general");
            String locationHint = extractJsonString(json, "locationHint", "");
            boolean needsWebSearch = json.contains("\"needsWebSearch\":true");

            if (keywords.isEmpty()) {
                keywords = Arrays.asList(query.split("\\s+"));
            }

            return SearchQueryAnalysis.builder()
                    .originalQuery(query)
                    .keywords(keywords)
                    .intentType(intentType)
                    .locationHint(locationHint)
                    .needsWebSearch(needsWebSearch)
                    .build();
        } catch (Exception e) {
            return fallbackAnalysis(query);
        }
    }

    private SearchQueryAnalysis fallbackAnalysis(String query) {
        return SearchQueryAnalysis.builder()
                .originalQuery(query)
                .keywords(Arrays.asList(query.split("\\s+")))
                .intentType("general")
                .locationHint("")
                .needsWebSearch(false)
                .build();
    }

    private List<String> extractJsonArray(String json, String key) {
        try {
            int start = json.indexOf("\"" + key + "\":[");
            if (start < 0) return List.of();
            int arrayStart = json.indexOf("[", start) + 1;
            int arrayEnd = json.indexOf("]", arrayStart);
            String content = json.substring(arrayStart, arrayEnd);
            return Arrays.stream(content.split(","))
                    .map(s -> s.trim().replaceAll("\"", ""))
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractJsonString(String json, String key, String defaultValue) {
        try {
            int start = json.indexOf("\"" + key + "\":\"");
            if (start < 0) return defaultValue;
            int valueStart = json.indexOf("\"", start + key.length() + 3) + 1;
            int valueEnd = json.indexOf("\"", valueStart);
            return json.substring(valueStart, valueEnd);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void saveHistory(String query, Long memberId) {
        if (memberId == null || query == null || query.isBlank()) return;
        try {
            String key = "search:history:" + memberId;
            redisTemplate.opsForZSet().add(key, query, System.currentTimeMillis());
            redisTemplate.expire(key, Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("[QueryAnalyzer] 검색 이력 저장 실패: {}", e.getMessage());
        }
    }
}
