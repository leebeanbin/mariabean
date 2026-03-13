package com.mariabean.reservation.notification.infrastructure.gmail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailTokenRedisAdapter implements GmailTokenStore {

    private static final String KEY_PREFIX = "gmail:token:";
    private static final String ACCESS_SUFFIX = ":access";
    private static final String REFRESH_SUFFIX = ":refresh";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long memberId, String accessToken, String refreshToken, long expiresInSeconds) {
        String accessKey = KEY_PREFIX + memberId + ACCESS_SUFFIX;
        String refreshKey = KEY_PREFIX + memberId + REFRESH_SUFFIX;
        redisTemplate.opsForValue().set(accessKey, accessToken, expiresInSeconds, TimeUnit.SECONDS);
        // refresh token TTL: 180일 (Gmail refresh tokens don't expire unless revoked)
        redisTemplate.opsForValue().set(refreshKey, refreshToken, 180L, TimeUnit.DAYS);
        log.debug("[GmailToken] saved for memberId={}", memberId);
    }

    @Override
    public Optional<GmailTokenEntry> findByMemberId(Long memberId) {
        String refreshKey = KEY_PREFIX + memberId + REFRESH_SUFFIX;
        String accessKey = KEY_PREFIX + memberId + ACCESS_SUFFIX;
        String refreshToken = redisTemplate.opsForValue().get(refreshKey);
        if (refreshToken == null) return Optional.empty();
        String accessToken = redisTemplate.opsForValue().get(accessKey);
        return Optional.of(new GmailTokenEntry(memberId, accessToken, refreshToken));
    }

    @Override
    public void delete(Long memberId) {
        redisTemplate.delete(KEY_PREFIX + memberId + ACCESS_SUFFIX);
        redisTemplate.delete(KEY_PREFIX + memberId + REFRESH_SUFFIX);
    }
}
