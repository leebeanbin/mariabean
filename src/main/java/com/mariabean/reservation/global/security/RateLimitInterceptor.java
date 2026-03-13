package com.mariabean.reservation.global.security;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // Simple in-memory bucket store per IP. For distributed env, use Redis-backed
    // Bucket4j.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> recentQueryByClient = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Apply only to specific high-cost endpoints like Google Places API Search
        if (request.getRequestURI().startsWith("/api/v1/facilities/places/search")) {
            String ip = getClientIP(request);
            String query = request.getParameter("query");
            String queryKey = ip + "|" + normalize(query);
            long now = System.currentTimeMillis();
            Long lastAt = recentQueryByClient.get(queryKey);
            if (lastAt != null && now - lastAt < 1200) {
                // 동일 query 재입력(IME 조합/재시도) 트래픽은 짧은 윈도우에서 토큰 차감 없이 통과.
                return true;
            }
            recentQueryByClient.put(queryKey, now);
            Bucket bucket = buckets.computeIfAbsent(ip, this::createNewBucket);

            if (bucket.tryConsume(1)) {
                return true;
            } else {
                log.warn("Rate limit exceeded for IP: {}", ip);
                // Return 429 Too Many Requests via BusinessException
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        }
        return true;
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.builder().capacity(120).refillGreedy(120, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
