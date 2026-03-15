package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.facility.application.analytics.MapSearchEventPublisher;
import com.mariabean.reservation.search.application.dto.*;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AIResearchOrchestratorTest {

    @InjectMocks
    private AIResearchOrchestrator orchestrator;

    @Mock private SearchQueryAnalyzerService queryAnalyzer;
    @Mock private HybridSearchService hybridSearch;
    @Mock private TavilyWebSearchService tavilySearch;
    @Mock private PlacePhotoEnrichmentService photoEnricher;
    @Mock private UserPlaceMemoService memoService;
    @Mock private AISearchRanker ranker;
    @Mock private AISummaryService summaryService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private MapSearchEventPublisher eventPublisher;
    @Mock private ValueOperations<String, String> valueOperations;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private SearchQueryAnalysis analysis(boolean needsWeb) {
        return SearchQueryAnalysis.builder()
                .originalQuery("강남 정형외과")
                .keywords(List.of("강남", "정형외과"))
                .intentType("hospital")
                .locationHint("강남")
                .needsWebSearch(needsWeb)
                .build();
    }

    private FacilitySearchDocument esDoc(String id) {
        return FacilitySearchDocument.builder()
                .id(id).name("테스트 병원").category("HOSPITAL").address("서울").placeId("p-" + id).build();
    }

    private AISearchResult aiResult(String placeId, String name) {
        return AISearchResult.builder()
                .id("id-" + placeId).placeId(placeId).name(name).score(0.7).build();
    }

    private AISummary summary(String text) {
        return AISummary.builder().summary(text).citations(List.of()).build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("모든 의존성이 정상 동작하면 query, summary, results가 있는 결과를 반환한다")
    void research_happyPath_returnsFullResult() throws Exception {
        // cache miss
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(queryAnalyzer.analyze(anyString(), any())).willReturn(analysis(false));
        given(hybridSearch.search(anyList(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(esDoc("f-1")));
        given(tavilySearch.search(anyString(), anyString())).willReturn(WebSearchResult.empty());
        given(photoEnricher.fetchPhotos(anyString(), isNull(), anyList())).willReturn(List.of());
        given(memoService.loadMemos(eq(1L), anyList())).willReturn(Map.of());

        AISearchResult ranked = aiResult("p-f-1", "테스트 병원");
        given(ranker.rankWithMemo(anyList(), anyMap(), anyDouble(), anyDouble(), eq(1L)))
                .willReturn(List.of(ranked));
        given(summaryService.summarize(anyString(), anyList(), any(WebSearchResult.class)))
                .willReturn(summary("강남 정형외과 요약"));

        // when
        AIResearchResult result = orchestrator.research("강남 정형외과", 37.5, 127.0, 1L);

        // then
        assertThat(result.getQuery()).isEqualTo("강남 정형외과");
        assertThat(result.getAiSummary().getSummary()).isEqualTo("강남 정형외과 요약");
        assertThat(result.getResults()).hasSize(1);
    }

    @Test
    @DisplayName("내부 검색 결과가 없으면 요약에 결과 없음 메시지가 담긴다")
    void research_internalSearchEmpty_returnsEmptySummary() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(queryAnalyzer.analyze(anyString(), any())).willReturn(analysis(false));
        given(hybridSearch.search(anyList(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());
        given(tavilySearch.search(anyString(), anyString())).willReturn(WebSearchResult.empty());
        given(memoService.loadMemos(isNull(), anyList())).willReturn(Map.of());
        given(ranker.rankWithMemo(anyList(), anyMap(), anyDouble(), anyDouble(), isNull()))
                .willReturn(List.of());
        given(summaryService.summarize(anyString(), eq(List.of()), any(WebSearchResult.class)))
                .willReturn(summary("검색 결과가 없습니다."));

        // when
        AIResearchResult result = orchestrator.research("존재안함", 37.5, 127.0, null);

        // then
        assertThat(result.getResults()).isEmpty();
        assertThat(result.getAiSummary().getSummary()).contains("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("Tavily는 항상 호출되어 웹 결과·인용이 포함된다")
    void research_tavilyAlwaysCalled() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(queryAnalyzer.analyze(anyString(), any())).willReturn(analysis(false));
        given(hybridSearch.search(anyList(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());
        given(tavilySearch.search(anyString(), anyString())).willReturn(WebSearchResult.empty());
        given(memoService.loadMemos(any(), anyList())).willReturn(Map.of());
        given(ranker.rankWithMemo(anyList(), anyMap(), anyDouble(), anyDouble(), any()))
                .willReturn(List.of());
        given(summaryService.summarize(anyString(), anyList(), any(WebSearchResult.class)))
                .willReturn(summary("요약"));

        // when
        orchestrator.research("내과", 37.5, 127.0, 1L);

        // then: Tavily는 needsWebSearch 관계없이 항상 호출된다
        verify(tavilySearch).search(anyString(), anyString());
    }

    @Test
    @DisplayName("Redis 캐시에 결과가 있으면 캐시에서 바로 반환하고 서비스를 호출하지 않는다")
    void research_cacheHit_returnsFromRedis() throws Exception {
        String cachedJson = """
                {"query":"강남 정형외과","aiSummary":{"summary":"캐시 요약","citations":[]},"results":[]}
                """;
        AIResearchResult cachedResult = AIResearchResult.builder()
                .query("강남 정형외과")
                .aiSummary(summary("캐시 요약"))
                .results(List.of())
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(cachedJson);
        given(objectMapper.readValue(eq(cachedJson), eq(AIResearchResult.class)))
                .willReturn(cachedResult);

        // when
        AIResearchResult result = orchestrator.research("강남 정형외과", 37.5, 127.0, 1L);

        // then
        assertThat(result.getQuery()).isEqualTo("강남 정형외과");
        verify(queryAnalyzer, never()).analyze(anyString(), any());
        verify(hybridSearch, never()).search(anyList(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("memberId가 null이면 개인 메모와 선호도를 로드하지 않는다")
    void research_nullMemberId_skipsMemosAndPrefs() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(queryAnalyzer.analyze(anyString(), isNull())).willReturn(analysis(false));
        given(hybridSearch.search(anyList(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());
        given(tavilySearch.search(anyString(), anyString())).willReturn(WebSearchResult.empty());
        given(memoService.loadMemos(isNull(), anyList())).willReturn(Map.of());
        given(ranker.rankWithMemo(anyList(), anyMap(), anyDouble(), anyDouble(), isNull()))
                .willReturn(List.of());
        given(summaryService.summarize(anyString(), anyList(), any(WebSearchResult.class)))
                .willReturn(summary("요약"));

        // when
        AIResearchResult result = orchestrator.research("치과", 37.5, 127.0, null);

        // then
        verify(memoService).loadMemos(isNull(), anyList());
        assertThat(result).isNotNull();
    }
}
