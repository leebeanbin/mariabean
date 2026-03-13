package com.mariabean.reservation.auth.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-in-seconds:3600}") long accessTokenValidityInSeconds,
            @Value("${jwt.refresh-token-validity-in-seconds:1209600}") long refreshTokenValidityInSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityInMilliseconds = accessTokenValidityInSeconds * 1000;
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInSeconds * 1000;
    }

    public String createAccessToken(String email, Long memberId, String role) {
        return createToken(email, memberId, role, accessTokenValidityInMilliseconds);
    }

    public String createRefreshToken(String email, Long memberId, String role) {
        return createToken(email, memberId, role, refreshTokenValidityInMilliseconds);
    }

    private String createToken(String email, Long memberId, String role, long validityInMilliseconds) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("memberId", memberId);
        claims.put("role", role);
        long now = (new Date()).getTime();
        Date validity = new Date(now + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(validity)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getPayload(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody().getSubject();
    }

    public Long getMemberId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("memberId", Long.class);
    }

    public String getRole(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
