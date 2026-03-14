package com.mariabean.reservation.facility.application.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MapAnalyticsConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = MapSearchEvent.TOPIC, groupId = "map-analytics-group", autoStartup = "${spring.kafka.enabled:true}")
    public void consume(String message) {
        try {
            MapSearchEvent event = objectMapper.readValue(message, MapSearchEvent.class);
            if (event.getQuery() == null || event.getQuery().isBlank()) return;

            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String popularityKey = "map:analytics:popular:" + date;
            String statKey = "map:analytics:stat:" + date;
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    operations.opsForZSet().incrementScore(popularityKey, event.getQuery().trim().toLowerCase(), 1.0);
                    operations.opsForHash().increment(statKey, "total", 1);
                    if (event.isCacheHit()) operations.opsForHash().increment(statKey, "cacheHit", 1);
                    if (event.isFallbackUsed()) operations.opsForHash().increment(statKey, "fallback", 1);
                    if (event.getProviderUsed() != null && !event.getProviderUsed().isBlank()) {
                        operations.opsForHash().increment(statKey, "provider:" + event.getProviderUsed().toUpperCase(), 1);
                    }
                    if (event.getHitStatus() != null && !event.getHitStatus().isBlank()) {
                        operations.opsForHash().increment(statKey, "hitStatus:" + event.getHitStatus().toUpperCase(), 1);
                    }
                    if (event.getQueryType() != null && !event.getQueryType().isBlank()) {
                        operations.opsForHash().increment(statKey, "queryType:" + event.getQueryType().toLowerCase(), 1);
                    }
                    if (event.getTopMatchType() != null && !event.getTopMatchType().isBlank()) {
                        operations.opsForHash().increment(statKey, "topMatchType:" + event.getTopMatchType().toUpperCase(), 1);
                    }
                    if (event.isSuggestionClicked()) {
                        operations.opsForHash().increment(statKey, "suggestion:clicked", 1);
                    } else {
                        operations.opsForHash().increment(statKey, "suggestion:served", 1);
                    }
                    operations.expire(popularityKey, Duration.ofDays(7));
                    operations.expire(statKey, Duration.ofDays(7));
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[MapAnalyticsConsumer] failed: {}", e.getMessage());
        }
    }
}

