package com.mariabean.reservation.auth.infrastructure.oauth2;

import com.mariabean.reservation.auth.domain.RefreshTokenRepository;
import com.mariabean.reservation.auth.domain.RefreshToken;
import com.mariabean.reservation.auth.application.JwtProvider;
import com.mariabean.reservation.notification.infrastructure.gmail.GmailTokenStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GmailTokenStore gmailTokenStore;
    private final OAuth2AuthorizedClientService authorizedClientService;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = (String) oAuth2User.getAttributes().get("email");
        Long memberId = ((Number) oAuth2User.getAttributes().get("memberId")).longValue();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        String accessToken = jwtProvider.createAccessToken(email, memberId, role);
        String refreshTokenString = jwtProvider.createRefreshToken(email, memberId, role);

        RefreshToken refreshToken = RefreshToken.builder()
                .email(email)
                .token(refreshTokenString)
                .expiration(1209600L) // 14 days in seconds
                .build();

        refreshTokenRepository.save(refreshToken);

        // Google 로그인 시 Gmail 토큰 저장
        if (authentication instanceof OAuth2AuthenticationToken oauthToken
                && "google".equals(oauthToken.getAuthorizedClientRegistrationId())) {
            try {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        "google", oauthToken.getName());
                if (client != null && client.getAccessToken() != null) {
                    String gmailAccess = client.getAccessToken().getTokenValue();
                    String gmailRefresh = client.getRefreshToken() != null
                            ? client.getRefreshToken().getTokenValue() : null;
                    if (gmailRefresh != null) {
                        gmailTokenStore.save(memberId, gmailAccess, gmailRefresh, 3600L);
                        log.info("[OAuth2] Gmail token saved for memberId={}", memberId);
                    }
                }
            } catch (Exception e) {
                log.warn("[OAuth2] Failed to save Gmail token for memberId={}: {}", memberId, e.getMessage());
            }
        }

        log.info("OAuth2 login success. Email: {}", email);

        // URL fragment(#)를 사용해 토큰 전달 — fragment는 서버로 전송되지 않아 서버 로그에 기록되지 않음
        String targetUrl = String.format(
                "%s/auth/callback#accessToken=%s&refreshToken=%s&memberId=%s",
                frontendUrl,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(refreshTokenString, StandardCharsets.UTF_8),
                memberId);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
