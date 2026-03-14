package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.AISearchResult;
import com.mariabean.reservation.search.domain.UserPlaceMemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AISearchRankerTest {

    @InjectMocks
    private AISearchRanker aiSearchRanker;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private AISearchResult searchResult(String placeId, String name, double score) {
        return AISearchResult.builder()
                .id("id-" + placeId)
                .placeId(placeId)
                .name(name)
                .category("HOSPITAL")
                .address("서울")
                .score(score)
                .build();
    }

    private UserPlaceMemo memo(String placeId, String content, int boostScore) {
        return UserPlaceMemo.builder()
                .id(1L)
                .memberId(1L)
                .placeId(placeId)
                .placeName("테스트 장소")
                .content(content)
                .boostScore(boostScore)
                .build();
    }

    private void stubTrending(Set<String> keywords) {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange(anyString(), eq(0L), eq(9L))).willReturn(keywords);
    }

    private void stubTrendingAndPrefs(Set<String> keywords) {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange(anyString(), eq(0L), eq(9L))).willReturn(keywords);
        given(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).willReturn(Set.of());
    }

    // ──────────────────────────────────────────────────────────────────────
    // tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("boostScore=5인 메모가 있는 시설은 최상위로 올라간다")
    void rank_memoHighlighted_boostedToTop() {
        // given
        AISearchResult low  = searchResult("p-low",  "일반 병원", 0.5);
        AISearchResult high = searchResult("p-high", "메모 병원", 0.3); // lower original score

        UserPlaceMemo highMemo = memo("p-high", "자주 가는 병원", 5);
        Map<String, UserPlaceMemo> memos = Map.of("p-high", highMemo);

        stubTrendingAndPrefs(Set.of());

        // when
        List<AISearchResult> ranked = aiSearchRanker.rankWithMemo(
                List.of(low, high), memos, 37.5, 127.0, 1L);

        // then  – memo boost: (5/5)*0.3 = 0.3  → 0.3+0.3=0.6, min(0.6)=0.6 > 0.5
        assertThat(ranked.get(0).getPlaceId()).isEqualTo("p-high");
        assertThat(ranked.get(0).isMemoHighlighted()).isTrue();
    }

    @Test
    @DisplayName("메모가 없으면 원래 점수 내림차순으로 정렬된다")
    void rank_noMemo_sortedByOriginalScore() {
        // given
        AISearchResult r1 = searchResult("p-1", "병원A", 0.9);
        AISearchResult r2 = searchResult("p-2", "병원B", 0.7);
        AISearchResult r3 = searchResult("p-3", "병원C", 0.5);

        stubTrendingAndPrefs(Set.of());

        // when
        List<AISearchResult> ranked = aiSearchRanker.rankWithMemo(
                List.of(r3, r1, r2), Map.of(), 37.5, 127.0, 1L);

        // then
        assertThat(ranked).extracting(AISearchResult::getPlaceId)
                .containsExactly("p-1", "p-2", "p-3");
    }

    @Test
    @DisplayName("낮은 점수(0.1) + 작은 메모 boost라도 최소 점수 0.6이 보장된다")
    void rank_memoBoost_minimumScore0Point6() {
        // given
        AISearchResult r = searchResult("p-x", "저점수 병원", 0.1);
        // boostScore=1 → boost=(1/5)*0.3=0.06, 0.1+0.06=0.16 < 0.6 → clamped to 0.6
        UserPlaceMemo m = memo("p-x", "내용", 1);

        stubTrendingAndPrefs(Set.of());

        // when
        List<AISearchResult> ranked = aiSearchRanker.rankWithMemo(
                List.of(r), Map.of("p-x", m), 37.5, 127.0, 1L);

        // then
        assertThat(ranked.get(0).getScore()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("트렌딩 키워드와 이름이 매칭되면 +0.1 boost가 적용된다")
    void rank_trendingKeywordMatch_adds0Point1Boost() {
        // given
        AISearchResult r = searchResult("p-trend", "강남 정형외과", 0.5);

        stubTrendingAndPrefs(Set.of("정형외과"));

        // when
        List<AISearchResult> ranked = aiSearchRanker.rankWithMemo(
                List.of(r), Map.of(), 37.5, 127.0, 1L);

        // then
        assertThat(ranked.get(0).getScore()).isEqualTo(0.5 + 0.1, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("memberId가 null이면 개인 선호도 boost가 적용되지 않는다")
    void rank_nullMemberId_noPreferenceBoost() {
        // given
        AISearchResult r = searchResult("p-anon", "익명 병원", 0.6);

        // trending only (no user pref lookup)
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange(anyString(), eq(0L), eq(9L))).willReturn(Set.of());
        // rangeWithScores should NOT be called because memberId is null

        // when
        List<AISearchResult> ranked = aiSearchRanker.rankWithMemo(
                List.of(r), Map.of(), 37.5, 127.0, null);

        // then
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getScore()).isEqualTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    }
}
