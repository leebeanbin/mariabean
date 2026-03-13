package com.mariabean.reservation.notification.application;

public record NotificationRecipient(
        Long memberId,
        String name,
        String email,
        String phoneNumber,
        String kakaoProviderId) {
}
