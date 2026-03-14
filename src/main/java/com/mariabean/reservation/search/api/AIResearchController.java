package com.mariabean.reservation.search.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.auth.domain.UserPrincipal;
import com.mariabean.reservation.search.application.AIResearchOrchestrator;
import com.mariabean.reservation.search.application.HybridSearchService;
import com.mariabean.reservation.search.application.PlacePhotoEnrichmentService;
import com.mariabean.reservation.search.application.AISummaryService;
import com.mariabean.reservation.search.application.TavilyWebSearchService;
import com.mariabean.reservation.search.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class AIResearchController {

    private final AIResearchOrchestrator orchestrator;
    private final HybridSearchService hybridSearch;
    private final PlacePhotoEnrichmentService photoEnricher;
    private final TavilyWebSearchService tavilySearch;
    private final AISummaryService summaryService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @GetMapping("/research")
    public ResponseEntity<AIResearchResult> research(
            @RequestParam String query,
            @RequestParam double lat,
            @RequestParam double lng,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long memberId = principal != null ? principal.getMemberId() : null;
        AIResearchResult result = orchestrator.research(query, lat, lng, memberId);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/research/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter researchStream(
            @RequestParam String query,
            @RequestParam double lat,
            @RequestParam double lng,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long memberId = principal != null ? principal.getMemberId() : null;
        SseEmitter emitter = new SseEmitter(30_000L);

        CompletableFuture.runAsync(() -> {
            try {
                // Phase 1: ES kNN 결과 즉시 전송
                List<com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument> initial =
                        hybridSearch.search(List.of(query), lat, lng, 5.0);
                List<AISearchResult> initialResults = initial.stream()
                        .map(doc -> AISearchResult.builder()
                                .id(doc.getId())
                                .placeId(doc.getPlaceId())
                                .name(doc.getName())
                                .category(doc.getCategory())
                                .address(doc.getAddress())
                                .score(0.5)
                                .build())
                        .collect(Collectors.toList());
                emitter.send(SseEmitter.event().name("initial").data(
                        objectMapper.writeValueAsString(initialResults)));

                // Phase 2: 사진 enrichment
                List<AISearchResult> enriched = initialResults.stream()
                        .map(r -> {
                            List<String> photos = photoEnricher.fetchPhotos(r.getPlaceId(), null, List.of());
                            return AISearchResult.builder()
                                    .id(r.getId()).placeId(r.getPlaceId()).name(r.getName())
                                    .category(r.getCategory()).address(r.getAddress())
                                    .photos(photos).score(r.getScore()).build();
                        })
                        .collect(Collectors.toList());
                emitter.send(SseEmitter.event().name("enriched").data(
                        objectMapper.writeValueAsString(enriched)));

                // Phase 3: Tavily + AI 요약
                WebSearchResult web = tavilySearch.search(query, "");
                AISummary summary = summaryService.summarize(query, enriched, web);
                emitter.send(SseEmitter.event().name("summary").data(
                        objectMapper.writeValueAsString(summary)));

                emitter.complete();
            } catch (Exception e) {
                log.warn("[SSE] 스트리밍 오류: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }, sseExecutor);

        return emitter;
    }

    @PostMapping("/research/click")
    public ResponseEntity<Void> recordClick(
            @RequestBody ClickFeedback feedback,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.ok().build();

        try {
            String key = "user:pref:" + principal.getMemberId();
            redisTemplate.opsForZSet().incrementScore(key, feedback.getPlaceId(), 1.0);
            redisTemplate.expire(key, Duration.ofDays(60));
        } catch (Exception e) {
            log.warn("[Click] 클릭 피드백 저장 실패: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
