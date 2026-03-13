package com.mariabean.reservation.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * HTTP 요청/응답 전체를 로깅하는 서블릿 필터.
 * - 요청: Method, URI, Headers, Body
 * - 응답: Status, Headers, Body (최대 1000자)
 * - 처리 시간 (ms)
 *
 * ContentCachingWrapper를 사용하여 body를 안전하게 읽으면서도
 * 다운스트림 핸들러에 영향을 주지 않습니다.
 */
@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LOG_LENGTH = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(wrappedRequest);
            logResponse(wrappedResponse, duration);
            wrappedResponse.copyBodyToResponse(); // 반드시 호출 — 응답 body를 클라이언트에 전달
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? uri + "?" + queryString : uri;

        String headers = Collections.list(request.getHeaderNames()).stream()
                .filter(name -> !name.equalsIgnoreCase("authorization")) // 보안: 토큰 숨김
                .map(name -> name + "=" + request.getHeader(name))
                .collect(Collectors.joining(", "));

        String body = truncate(new String(request.getContentAsByteArray(), StandardCharsets.UTF_8));

        log.info("[HTTP REQ] {} {} | headers=[{}] | body={}", method, fullUri, headers, body);
    }

    private void logResponse(ContentCachingResponseWrapper response, long durationMs) {
        int status = response.getStatus();
        String body = truncate(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));

        log.info("[HTTP RES] status={} | duration={}ms | body={}", status, durationMs, body);
    }

    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return "(empty)";
        }
        return text.length() > MAX_BODY_LOG_LENGTH
                ? text.substring(0, MAX_BODY_LOG_LENGTH) + "...(truncated)"
                : text;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 정적 리소스, 헬스체크 등은 로깅 제외
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/favicon");
    }
}
