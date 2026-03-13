package com.mariabean.reservation.notification.infrastructure.gmail;

import java.util.Optional;

public interface GmailTokenStore {
    void save(Long memberId, String accessToken, String refreshToken, long expiresInSeconds);
    Optional<GmailTokenEntry> findByMemberId(Long memberId);
    void delete(Long memberId);
}
