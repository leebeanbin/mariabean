package com.mariabean.reservation.auth.infrastructure.persistence;

import com.mariabean.reservation.auth.domain.RefreshToken;
import com.mariabean.reservation.auth.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenRedisRepository redisRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenRedisEntity entity = RefreshTokenRedisEntity.builder()
                .email(refreshToken.getEmail())
                .token(refreshToken.getToken())
                .expiration(refreshToken.getExpiration())
                .build();
        redisRepository.save(entity);
        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findById(String email) {
        return redisRepository.findById(email).map(entity -> RefreshToken.builder()
                .email(entity.getEmail())
                .token(entity.getToken())
                .expiration(entity.getExpiration())
                .build());
    }

    @Override
    public void deleteById(String email) {
        redisRepository.deleteById(email);
    }
}
