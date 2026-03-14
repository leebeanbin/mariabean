package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.domain.UserPlaceMemo;
import com.mariabean.reservation.search.domain.UserPlaceMemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPlaceMemoService {

    private final UserPlaceMemoRepository memoRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final String CACHE_PREFIX = "user_memo:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Transactional
    public UserPlaceMemo saveMemo(Long memberId, String placeId, String placeName,
                                  String content, int boostScore) {
        UserPlaceMemo memo = memoRepository.findByMemberIdAndPlaceId(memberId, placeId)
                .map(existing -> {
                    existing.update(content, boostScore);
                    return existing;
                })
                .orElseGet(() -> UserPlaceMemo.builder()
                        .memberId(memberId)
                        .placeId(placeId)
                        .placeName(placeName)
                        .content(content)
                        .boostScore(boostScore)
                        .build());
        UserPlaceMemo saved = memoRepository.save(memo);
        evictCache(memberId, placeId);
        return saved;
    }

    @Transactional
    public UserPlaceMemo updateMemo(Long memoId, Long memberId, String content, int boostScore) {
        UserPlaceMemo memo = memoRepository.findById(memoId)
                .filter(m -> m.getMemberId().equals(memberId))
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다."));
        memo.update(content, boostScore);
        UserPlaceMemo saved = memoRepository.save(memo);
        evictCache(memberId, memo.getPlaceId());
        return saved;
    }

    @Transactional
    public void deleteMemo(Long memoId, Long memberId) {
        UserPlaceMemo memo = memoRepository.findById(memoId)
                .filter(m -> m.getMemberId().equals(memberId))
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다."));
        memoRepository.delete(memo);
        evictCache(memberId, memo.getPlaceId());
    }

    @Transactional(readOnly = true)
    public Map<String, UserPlaceMemo> loadMemos(Long memberId, List<String> placeIds) {
        if (memberId == null || placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }
        return memoRepository.findByMemberIdAndPlaceIdIn(memberId, placeIds)
                .stream()
                .collect(Collectors.toMap(UserPlaceMemo::getPlaceId, m -> m));
    }

    @Transactional(readOnly = true)
    public UserPlaceMemo getMemo(Long memberId, String placeId) {
        return memoRepository.findByMemberIdAndPlaceId(memberId, placeId).orElse(null);
    }

    private void evictCache(Long memberId, String placeId) {
        try {
            redisTemplate.delete(CACHE_PREFIX + memberId + ":" + placeId);
        } catch (Exception e) {
            log.debug("[MemoService] 캐시 삭제 실패: {}", e.getMessage());
        }
    }
}
