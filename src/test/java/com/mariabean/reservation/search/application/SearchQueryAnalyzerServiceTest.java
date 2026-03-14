package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.SearchQueryAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchQueryAnalyzerServiceTest {

    @InjectMocks
    private SearchQueryAnalyzerService searchQueryAnalyzerService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec promptSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    // ──────────────────────────────────────────────────────────────────────
    // analyze()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LLM이 유효한 JSON을 반환하면 키워드와 인텐트가 올바르게 파싱된다")
    void analyze_llmSuccess_returnsKeywordsAndIntent() {
        // given
        String query = "강남 정형외과";
        String llmJson = """
                {"keywords":["강남","정형외과"],"intentType":"hospital","locationHint":"강남","needsWebSearch":true}
                """;

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(llmJson);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // when
        SearchQueryAnalysis result = searchQueryAnalyzerService.analyze(query, 1L);

        // then
        assertThat(result.getKeywords()).containsExactly("강남", "정형외과");
        assertThat(result.getIntentType()).isEqualTo("hospital");
        assertThat(result.getLocationHint()).isEqualTo("강남");
        assertThat(result.isNeedsWebSearch()).isTrue();
        assertThat(result.getOriginalQuery()).isEqualTo(query);
    }

    @Test
    @DisplayName("LLM이 RuntimeException을 던지면 쿼리를 공백으로 분리한 fallback을 반환한다")
    void analyze_llmThrows_returnsFallbackAnalysis() {
        // given
        String query = "서울 피부과 추천";

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willThrow(new RuntimeException("LLM 연결 실패"));

        // when
        SearchQueryAnalysis result = searchQueryAnalyzerService.analyze(query, null);

        // then
        assertThat(result.getKeywords()).containsExactlyInAnyOrder("서울", "피부과", "추천");
        assertThat(result.getIntentType()).isEqualTo("general");
        assertThat(result.isNeedsWebSearch()).isFalse();
    }

    @Test
    @DisplayName("LLM이 JSON이 아닌 텍스트를 반환하면 fallback 분석 결과를 반환한다")
    void analyze_malformedJson_returnsFallback() {
        // given
        String query = "안과 강남구";
        String badResponse = "죄송합니다. 현재 서비스를 이용할 수 없습니다.";

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(badResponse);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // when
        SearchQueryAnalysis result = searchQueryAnalyzerService.analyze(query, 1L);

        // then
        // keywords 필드가 빈 배열로 파싱돼 fallback으로 query.split 결과가 들어간다
        assertThat(result.getKeywords()).containsExactlyInAnyOrder("안과", "강남구");
        assertThat(result.getOriginalQuery()).isEqualTo(query);
    }

    @Test
    @DisplayName("memberId가 non-null이면 Redis ZSet에 검색 이력이 저장된다")
    void analyze_savesHistoryToRedis() {
        // given
        String query = "내과 예약";
        String llmJson = """
                {"keywords":["내과"],"intentType":"hospital","locationHint":"","needsWebSearch":false}
                """;

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(llmJson);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // when
        searchQueryAnalyzerService.analyze(query, 42L);

        // then
        verify(zSetOperations).add(eq("search:history:42"), eq(query), anyDouble());
        verify(redisTemplate).expire(eq("search:history:42"), any(Duration.class));
    }

    @Test
    @DisplayName("memberId가 null이면 Redis에 검색 이력을 저장하지 않는다")
    void analyze_nullMemberId_skipsHistory() {
        // given
        String query = "치과";
        String llmJson = """
                {"keywords":["치과"],"intentType":"general","locationHint":"","needsWebSearch":false}
                """;

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(llmJson);

        // when
        searchQueryAnalyzerService.analyze(query, null);

        // then
        verify(redisTemplate, never()).opsForZSet();
    }

    // ──────────────────────────────────────────────────────────────────────
    // saveHistory()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveHistory에 빈 쿼리가 전달되면 Redis 쓰기를 건너뛴다")
    void saveHistory_blankQuery_skips() {
        // when
        searchQueryAnalyzerService.saveHistory("   ", 1L);

        // then
        verify(redisTemplate, never()).opsForZSet();
    }
}
