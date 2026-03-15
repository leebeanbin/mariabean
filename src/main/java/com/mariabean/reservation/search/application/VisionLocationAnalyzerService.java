package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionLocationAnalyzerService {

    private final ChatClient chatClient;

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private static final String VISION_PROMPT = """
            이미지의 장소·건물·랜드마크를 분석하세요.
            JSON 형식으로만 응답하세요:
            {"locationDescription":"장소 설명","landmarks":["랜드마크1","랜드마크2"],
            "suggestedQuery":"한국어 검색어","confidence":0.8}
            """;

    public VisionSearchResult analyzeImage(byte[] imageBytes, String mimeType) {
        try {
            // Spring AI 1.0.0: UserMessage.builder() — constructor is private
            UserMessage msg = UserMessage.builder()
                    .text(VISION_PROMPT)
                    .media(new Media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes)))
                    .build();

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
            validateImageUrl(imageUrl);
            RestTemplate restTemplate = new RestTemplate();
            byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
            if (imageBytes == null) return fallbackVisionResult();

            String mimeType = detectMimeType(imageUrl);
            return analyzeImage(imageBytes, mimeType);
        } catch (IllegalArgumentException e) {
            log.warn("[Vision] URL 검증 실패: {}", e.getMessage());
            return fallbackVisionResult();
        } catch (Exception e) {
            log.warn("[Vision] URL 이미지 분석 실패: {}", e.getMessage());
            return fallbackVisionResult();
        }
    }

    /**
     * SSRF 방지: HTTPS 전용, 사설 IP 차단, 이미지 확장자 허용 목록 검증.
     */
    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("URL이 비어있습니다");
        }
        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 형식이 잘못되었습니다: " + imageUrl);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("HTTPS URL만 허용됩니다");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL에 호스트가 없습니다");
        }
        // DNS 조회 전 리터럴 사설 IP 차단
        if (isPrivateHost(host)) {
            throw new IllegalArgumentException("내부 네트워크 주소는 허용되지 않습니다");
        }
        // DNS 조회 후 실제 IP가 사설 범위인지 재확인 (DNS rebinding 방지)
        try {
            InetAddress resolved = InetAddress.getByName(host);
            if (resolved.isLoopbackAddress() || resolved.isSiteLocalAddress()
                    || resolved.isLinkLocalAddress() || resolved.isAnyLocalAddress()) {
                throw new IllegalArgumentException("내부 네트워크 주소로 해석되는 호스트는 허용되지 않습니다");
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("호스트를 찾을 수 없습니다: " + host);
        }
        // 이미지 확장자 허용 목록
        String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        boolean hasAllowedExtension = ALLOWED_IMAGE_EXTENSIONS.stream()
                .anyMatch(path::endsWith);
        if (!hasAllowedExtension) {
            throw new IllegalArgumentException("허용된 이미지 확장자가 아닙니다 (jpg/jpeg/png/gif/webp)");
        }
    }

    private boolean isPrivateHost(String host) {
        String lower = host.toLowerCase();
        return lower.equals("localhost")
                || lower.equals("::1")
                || lower.startsWith("127.")
                || lower.startsWith("10.")
                || lower.startsWith("192.168.")
                || (lower.startsWith("172.") && isPrivate172(lower))
                || lower.endsWith(".local")
                || lower.endsWith(".internal");
    }

    private boolean isPrivate172(String host) {
        // 172.16.0.0/12 (172.16.x.x ~ 172.31.x.x)
        try {
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (NumberFormatException ignored) {
            // not a numeric IP, let DNS rebinding check handle it
        }
        return false;
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
