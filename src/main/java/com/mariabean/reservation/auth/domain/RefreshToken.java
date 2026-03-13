package com.mariabean.reservation.auth.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * Pure Domain Model for RefreshToken.
 */
@Getter
public class RefreshToken {

    private String email;
    private String token;
    private Long expiration;

    @Builder
    public RefreshToken(String email, String token, Long expiration) {
        this.email = email;
        this.token = token;
        this.expiration = expiration;
    }
}
