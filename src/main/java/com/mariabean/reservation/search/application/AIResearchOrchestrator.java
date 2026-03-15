package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.facility.application.analytics.MapSearchEvent;
import com.mariabean.reservation.facility.application.analytics.MapSearchEventPublisher;
import com.mariabean.reservation.search.application.dto.*;
import com.mariabean.reservation.search.domain.UserPlaceMemo;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIResearchOrchestrator {

    private final SearchQueryAnalyzerService queryAnalyzer;
    private final HybridSearchService hybridSearch;
    private final TavilyWebSearchService tavilySearch;
    private final PlacePhotoEnrichmentService photoEnricher;
    private final UserPlaceMemoService memoService;
    private final AISearchRanker ranker;
    private final AISummaryService summaryService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MapSearchEventPublisher eventPublisher;

    private static final String CACHE_PREFIX = "ai_search:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(90);

    public AIResearchResult research(String query, double lat, double lng, Long memberId) {
        long start = System.currentTimeMillis();

        // 캐시 체크
        String cacheKey = CACHE_PREFIX + query.hashCode() + ":" + (int)(lat * 100) + ":" + (int)(lng * 100);
        AIResearchResult cached = getFromCache(cacheKey);
        if (cached != null) return cached;

        // 1. LLM 분석: intentType, keywords, needsWebSearch 추출
        SearchQueryAnalysis analysis;
        try {
            analysis = queryAnalyzer.analyze(query, memberId);
        } catch (Exception e) {
            log.warn("[Orchestrator] 쿼리 분석 실패: {}", e.getMessage());
            analysis = SearchQueryAnalysis.fallback(query);
        }

        // 2. 내부 하이브리드 검색
        List<FacilitySearchDocument> internal;
        try {
            internal = hybridSearch.search(List.of(query), lat, lng, 5.0);
        } catch (Exception e) {
            log.warn("[Orchestrator] 내부 검색 실패: {}", e.getMessage());
            internal = List.of();
        }

        // 3. Tavily 웹 검색 (needsWebSearch=true 일 때만)
        WebSearchResult web;
        if (analysis.isNeedsWebSearch()) {
            try {
                web = tavilySearch.search(query, "");
            } catch (Exception e) {
                log.warn("[Orchestrator] Tavily 검색 실패: {}", e.getMessage());
                web = WebSearchResult.empty();
            }
        } else {
            web = WebSearchResult.empty();
        }

        // 3. 내부 결과를 AISearchResult로 변환 + 사진 enrichment
        List<AISearchResult> enriched = enrichResults(internal, web.getImageUrls());

        // 4. 개인 메모 오버레이 + 재랭킹
        List<String> placeIds = enriched.stream()
                .map(AISearchResult::getPlaceId)
                .filter(pid -> pid != null)
                .collect(Collectors.toList());
        Map<String, UserPlaceMemo> memos = memoService.loadMemos(memberId, placeIds);
        List<AISearchResult> ranked = ranker.rankWithMemo(enriched, memos, lat, lng, memberId);

        // 5. AI 요약 생성
        AISummary summary = summaryService.summarize(query, ranked, web);

        AIResearchResult result = AIResearchResult.builder()
                .query(query)
                .aiSummary(summary)
                .results(ranked)
                .build();

        // 6. 캐시 저장 + 이벤트 발행
        saveToCache(cacheKey, result);
        publishSearchEvent(query, analysis, internal.size(), web, System.currentTimeMillis() - start);
        queryAnalyzer.saveHistory(query, memberId);

        return result;
    }

    private List<AISearchResult> enrichResults(List<FacilitySearchDocument> docs,
                                               List<String> webImages) {
        return docs.stream().map(doc -> {
            List<String> photos = photoEnricher.fetchPhotos(doc.getPlaceId(), null, webImages);
            return AISearchResult.builder()
                    .id(doc.getId())
                    .placeId(doc.getPlaceId())
                    .name(doc.getName())
                    .category(doc.getCategory())
                    .address(doc.getAddress())
                    .photos(photos)
                    .tags(doc.getSpecialties())
                    .score(0.5)
                    .build();
        }).collect(Collectors.toList());
    }

    private AIResearchResult getFromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, AIResearchResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveToCache(String key, AIResearchResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), CACHE_TTL);
        } catch (Exception e) {
            log.debug("[Orchestrator] 캐시 저장 실패: {}", e.getMessage());
        }
    }

    private void publishSearchEvent(String query, SearchQueryAnalysis analysis,
                                    int resultCount, WebSearchResult web, long elapsed) {
        try {
            MapSearchEvent event = MapSearchEvent.builder()
                    .query(query)
                    .resultCount(resultCount)
                    .elapsedMs(elapsed)
                    .hitStatus(resultCount > 0 ? "HIT" : "MISS")
                    .aiKeywords(analysis.getKeywords())
                    .intentType(analysis.getIntentType())
                    .hybridSearchUsed(true)
                    .tavilyUsed(web != null && !web.getSnippets().isEmpty())
                    .timestamp(System.currentTimeMillis())
                    .build();
            eventPublisher.publish(event);
        } catch (Exception e) {
            log.debug("[Orchestrator] 이벤트 발행 실패: {}", e.getMessage());
        }
    }
}
