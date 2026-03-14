package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionLocationAnalyzerService {

    private final ChatClient chatClient;

    private static final String VISION_PROMPT = """
            이미지의 장소·건물·랜드마크를 분석하세요.
            JSON 형식으로만 응답하세요:
            {"locationDescription":"장소 설명","landmarks":["랜드마크1","랜드마크2"],
            "suggestedQuery":"한국어 검색어","confidence":0.8}
            """;

    public VisionSearchResult analyzeImage(byte[] imageBytes, String mimeType) {
        try {
            // Spring AI 1.0.0: UserMessage with media list
            UserMessage msg = new UserMessage(
                    VISION_PROMPT,
                    List.of(new Media(MimeType.valueOf(mimeType), imageBytes)));

            String response = chatClient.prompt()
                    .messages(msg)
                    .call()
                    .content();

            return parseVisionResult(response);
        } catch (Exception e) {
            log.warn("[Vision] 이미지 분석 실패: {}", e.getMessage());
            return fallbackVisionResult();
        }
    }

    public VisionSearchResult analyzeImageUrl(String imageUrl) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
            if (imageBytes == null) return fallbackVisionResult();

            String mimeType = detectMimeType(imageUrl);
            return analyzeImage(imageBytes, mimeType);
        } catch (Exception e) {
            log.warn("[Vision] URL 이미지 분석 실패: {}", e.getMessage());
            return fallbackVisionResult();
        }
    }

    private VisionSearchResult parseVisionResult(String json) {
        try {
            String desc = extractJsonString(json, "locationDescription", "");
            String query = extractJsonString(json, "suggestedQuery", "");
            double confidence = extractJsonDouble(json, "confidence", 0.5);
            List<String> landmarks = extractJsonArray(json, "landmarks");

            return VisionSearchResult.builder()
                    .locationDescription(desc)
                    .landmarks(landmarks)
                    .suggestedQuery(query)
                    .confidence(confidence)
                    .build();
        } catch (Exception e) {
            return fallbackVisionResult();
        }
    }

    private VisionSearchResult fallbackVisionResult() {
        return VisionSearchResult.builder()
                .locationDescription("")
                .landmarks(List.of())
                .suggestedQuery("")
                .confidence(0.0)
                .build();
    }

    private String detectMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String extractJsonString(String json, String key, String defaultValue) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search) + search.length();
            if (start < search.length()) return defaultValue;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double extractJsonDouble(String json, String key, double defaultValue) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search) + search.length();
            if (start < search.length()) return defaultValue;
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (Exception e) {
            return defaultValue;
        }
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
}
