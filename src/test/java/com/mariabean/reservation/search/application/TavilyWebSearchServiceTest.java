package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.search.application.dto.WebSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TavilyWebSearchServiceTest {

    // ──────────────────────────────────────────────────────────────────────
    // Test 1 & 2 & 3 : blank-key path + JSON parsing (no HTTP call needed)
    // ──────────────────────────────────────────────────────────────────────

    @InjectMocks
    private TavilyWebSearchService tavilyWebSearchService;

    @Mock
    private ObjectMapper objectMapper;   // injected but not used in the blank-key path

    @Test
    @DisplayName("API 키가 비어 있으면 HTTP 호출 없이 WebSearchResult.empty()를 반환한다")
    void search_noApiKey_returnsEmpty() {
        // given – tavilyApiKey is "" by default via @Value fallback
        ReflectionTestUtils.setField(tavilyWebSearchService, "tavilyApiKey", "");

        // when
        WebSearchResult result = tavilyWebSearchService.search("강남 정형외과", "강남");

        // then
        assertThat(result.getSnippets()).isEmpty();
        assertThat(result.getImageUrls()).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // JSON-parsing tests use a real ObjectMapper + a separate service instance
    // ──────────────────────────────────────────────────────────────────────

    private TavilyWebSearchService serviceWithRealMapper() {
        TavilyWebSearchService svc = new TavilyWebSearchService(new ObjectMapper());
        // Keep apiKey blank so the public search() returns early — we test the private
        // parsing path indirectly by supplying the JSON through a subclass approach.
        // Instead, we call search() with a blank key and verify the empty fallback:
        // The actual parsing tests are covered by test 2 below which uses a real
        // ObjectMapper but the key set to some non-blank value will cause an HTTP call.
        // Solution: we verify the parsing logic via search() feeding a stub response,
        // but since RestTemplate is newed inside the method we test via reflection or
        // rely on the graceful-catch path (exception → empty).
        return svc;
    }

    @Test
    @DisplayName("유효한 Tavily JSON 응답이 파싱되면 snippets와 imageUrls가 채워진다")
    void search_validJson_parsesSnippetsAndImages() throws Exception {
        // We exercise the parsing path by constructing the response JSON ourselves
        // and calling parseTavilyResponse via reflection since RestTemplate is internal.
        String tavilyJson = """
                {
                  "results": [
                    {
                      "title": "강남 정형외과 후기",
                      "url": "https://example.com/review",
                      "content": "강남구 위치한 정형외과로 친절하고 예약이 빠릅니다.",
                      "score": 0.92
                    }
                  ],
                  "images": [
                    "https://example.com/image1.jpg",
                    "http://insecure.com/image2.jpg"
                  ]
                }
                """;

        // Use a real service with a real ObjectMapper
        TavilyWebSearchService realSvc = new TavilyWebSearchService(new ObjectMapper());

        // Invoke private parseTavilyResponse via reflection
        java.lang.reflect.Method parseMethod = TavilyWebSearchService.class
                .getDeclaredMethod("parseTavilyResponse", String.class);
        parseMethod.setAccessible(true);
        WebSearchResult result = (WebSearchResult) parseMethod.invoke(realSvc, tavilyJson);

        // then
        assertThat(result.getSnippets()).hasSize(1);
        assertThat(result.getSnippets().get(0).getTitle()).isEqualTo("강남 정형외과 후기");
        assertThat(result.getSnippets().get(0).getScore()).isEqualTo(0.92);
        // only https:// images pass the filter
        assertThat(result.getImageUrls()).containsExactly("https://example.com/image1.jpg");
    }

    @Test
    @DisplayName("Tavily 응답이 JSON이 아니면 WebSearchResult.empty()를 반환한다")
    void search_malformedJson_returnsEmpty() throws Exception {
        TavilyWebSearchService realSvc = new TavilyWebSearchService(new ObjectMapper());

        java.lang.reflect.Method parseMethod = TavilyWebSearchService.class
                .getDeclaredMethod("parseTavilyResponse", String.class);
        parseMethod.setAccessible(true);

        WebSearchResult result = (WebSearchResult) parseMethod.invoke(realSvc, "not-a-json-{{{");

        assertThat(result.getSnippets()).isEmpty();
        assertThat(result.getImageUrls()).isEmpty();
    }
}
