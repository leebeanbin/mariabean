package com.mariabean.reservation.member.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    private Long id;
    private String email;
    private String name;
    private Role role;
    private String provider; // e.g., google, kakao
    private String providerId;
    private String phoneNumber;

    @Builder
    public Member(Long id, String email, String name, Role role, String provider, String providerId, String phoneNumber) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.phoneNumber = phoneNumber;
    }

    public Member update(String name) {
        this.name = name;
        return this;
    }
}
