package com.mariabean.reservation.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    GUEST("ROLE_GUEST", "Guest User"),
    USER("ROLE_USER", "General User"),
    ADMIN("ROLE_ADMIN", "Administrator");

    private final String key;
    private final String title;
}
