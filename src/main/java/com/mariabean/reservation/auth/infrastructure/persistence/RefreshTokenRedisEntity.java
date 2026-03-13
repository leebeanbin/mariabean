package com.mariabean.reservation.auth.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@RedisHash("refreshToken")
public class RefreshTokenRedisEntity {

    @Id
    private String email;
    private String token;

    @TimeToLive
    private Long expiration;

    @Builder
    public RefreshTokenRedisEntity(String email, String token, Long expiration) {
        this.email = email;
        this.token = token;
        this.expiration = expiration;
    }
}
