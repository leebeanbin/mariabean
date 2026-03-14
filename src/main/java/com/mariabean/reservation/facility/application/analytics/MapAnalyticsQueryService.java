package com.mariabean.reservation.facility.application.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MapAnalyticsQueryService {

    private final StringRedisTemplate redisTemplate;

    public List<String> getPopularKeywords(int size) {
        try {
            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = "map:analytics:popular:" + date;
            Set<String> values = redisTemplate.opsForZSet()
                    .reverseRange(key, 0, Math.max(size - 1, 0));
            if (values == null || values.isEmpty()) return List.of();
            return values.stream().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void incrementPopularKeyword(String query) {
        if (query == null || query.isBlank()) return;
        try {
            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = "map:analytics:popular:" + date;
            redisTemplate.opsForZSet().incrementScore(key, query.trim().toLowerCase(), 1.0);
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (Exception ignore) {
            // no-op for redis outages
        }
    }
}

