package com.mariabean.reservation.notification.infrastructure.gmail;

public record GmailTokenEntry(
        Long memberId,
        String accessToken,
        String refreshToken
) {}
