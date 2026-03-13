package com.mariabean.reservation.member.domain;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);

    Optional<Member> findById(Long memberId);

    Optional<Member> findByEmail(String email);

    Member getByEmail(String email);

    Optional<Member> findByProviderAndProviderId(String provider, String providerId);
}
