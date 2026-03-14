package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.AISearchResult;
import com.mariabean.reservation.search.domain.UserPlaceMemo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AISearchRanker {

    private final RedisTemplate<String, String> redisTemplate;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 메모 boost + 트렌딩 boost + 클릭 선호도 boost 통합 랭킹
     */
    public List<AISearchResult> rankWithMemo(
            List<AISearchResult> results,
            Map<String, UserPlaceMemo> memos,
            double userLat, double userLng,
            Long memberId) {

        Set<String> trending = getTrendingKeywords();
        Map<String, Double> userPrefs = getUserPrefs(memberId);

        return results.stream()
                .map(result -> {
                    double score = result.getScore();
                    boolean memoHighlighted = false;
                    String userMemo = null;

                    // 메모 boost
                    UserPlaceMemo memo = memos.get(result.getPlaceId());
                    if (memo != null) {
                        double memoBoost = (memo.getBoostScore() / 5.0) * 0.3;
                        score = Math.max(score + memoBoost, 0.6); // 최소 0.6 보장
                        memoHighlighted = true;
                        userMemo = memo.getContent();
                    }

                    // 트렌딩 boost
                    if (facilityMatchesTrending(result, trending)) {
                        score += 0.1;
                    }

                    // 클릭 선호도 boost (최대 +0.15)
                    if (memberId != null && userPrefs.containsKey(result.getPlaceId())) {
                        double prefScore = Math.min(userPrefs.get(result.getPlaceId()) / 10.0, 0.15);
                        score += prefScore;
                    }

                    return AISearchResult.builder()
                            .id(result.getId())
                            .placeId(result.getPlaceId())
                            .name(result.getName())
                            .category(result.getCategory())
                            .address(result.getAddress())
                            .latitude(result.getLatitude())
                            .longitude(result.getLongitude())
                            .photos(result.getPhotos())
                            .rating(result.getRating())
                            .reviewCount(result.getReviewCount())
                            .tags(result.getTags())
                            .webSnippet(result.getWebSnippet())
                            .webUrl(result.getWebUrl())
                            .userMemo(userMemo)
                            .memoHighlighted(memoHighlighted)
                            .score(score)
                            .highlighted(score >= 0.8 || memoHighlighted)
                            .distanceMeters(result.getDistanceMeters())
                            .openNow(result.getOpenNow())
                            .build();
                })
                .sorted(Comparator.comparingDouble(AISearchResult::getScore).reversed())
                .toList();
    }

    private Set<String> getTrendingKeywords() {
        try {
            String key = "map:analytics:popular:" + LocalDate.now().format(DATE_FMT);
            Set<String> trending = redisTemplate.opsForZSet().reverseRange(key, 0, 9);
            return trending != null ? trending : Set.of();
        } catch (Exception e) {
            return Set.of();
        }
    }

    private Map<String, Double> getUserPrefs(Long memberId) {
        if (memberId == null) return Map.of();
        try {
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().rangeWithScores("user:pref:" + memberId, 0, -1);
            if (tuples == null) return Map.of();
            return tuples.stream()
                    .filter(t -> t.getValue() != null)
                    .collect(Collectors.toMap(
                            t -> t.getValue(),
                            t -> t.getScore() != null ? t.getScore() : 0.0
                    ));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean facilityMatchesTrending(AISearchResult result, Set<String> trending) {
        if (trending.isEmpty() || result.getName() == null) return false;
        String name = result.getName().toLowerCase();
        return trending.stream().anyMatch(kw -> name.contains(kw.toLowerCase()));
    }
}
