package com.mariabean.reservation.search.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.auth.infrastructure.security.JwtAuthenticationFilter;
import com.mariabean.reservation.search.application.*;
import com.mariabean.reservation.search.application.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import com.mariabean.reservation.support.WithMockUserPrincipal;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AIResearchController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
class AIResearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private AIResearchOrchestrator orchestrator;
    @MockBean private HybridSearchService hybridSearch;
    @MockBean private PlacePhotoEnrichmentService photoEnricher;
    @MockBean private TavilyWebSearchService tavilySearch;
    @MockBean private AISummaryService summaryService;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private ZSetOperations<String, String> zSetOperations;

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/v1/search/research
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/search/research — 익명 사용자도 200 응답과 결과를 받는다")
    void getResearch_anonymous_returns200() throws Exception {
        AIResearchResult fakeResult = AIResearchResult.builder()
                .query("강남 정형외과")
                .aiSummary(AISummary.builder().summary("요약 내용").citations(List.of()).build())
                .results(List.of())
                .build();

        given(orchestrator.research(anyString(), anyDouble(), anyDouble(), isNull()))
                .willReturn(fakeResult);

        mockMvc.perform(get("/api/v1/search/research")
                        .param("query", "강남 정형외과")
                        .param("lat", "37.5")
                        .param("lng", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("강남 정형외과"))
                .andExpect(jsonPath("$.aiSummary.summary").value("요약 내용"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /api/v1/search/research/click
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("POST /api/v1/search/research/click — 인증된 사용자가 클릭 피드백을 전송하면 200이 반환된다")
    void postClick_authenticated_returns200() throws Exception {
        ClickFeedback feedback = new ClickFeedback();
        // set via reflection-friendly approach — ClickFeedback has @NoArgsConstructor
        String feedbackJson = """
                {"placeId":"kakao-123","query":"강남 정형외과","rank":1}
                """;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.incrementScore(anyString(), anyString(), anyDouble())).willReturn(1.0);

        mockMvc.perform(post("/api/v1/search/research/click")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("POST /api/v1/search/research/click — 익명 사용자는 no-op으로 200이 반환된다")
    void postClick_anonymous_returns200NoOp() throws Exception {
        String feedbackJson = """
                {"placeId":"kakao-456","query":"내과","rank":2}
                """;

        mockMvc.perform(post("/api/v1/search/research/click")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson))
                .andExpect(status().isOk());
    }
}
