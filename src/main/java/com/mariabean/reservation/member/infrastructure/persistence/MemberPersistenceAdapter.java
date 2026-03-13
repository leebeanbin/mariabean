package com.mariabean.reservation.member.infrastructure.persistence;

import com.mariabean.reservation.member.domain.Member;
import com.mariabean.reservation.member.domain.MemberRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberPersistenceAdapter implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;
    private final JPAQueryFactory queryFactory;

    private static final QMemberJpaEntity member = QMemberJpaEntity.memberJpaEntity;

    private BooleanExpression isNotDeleted() {
        return member.deletedAt.isNull();
    }

    @Override
    public Member save(Member m) {
        MemberJpaEntity entity = MemberJpaEntity.builder()
                .id(m.getId())
                .email(m.getEmail())
                .name(m.getName())
                .role(m.getRole())
                .provider(m.getProvider())
                .providerId(m.getProviderId())
                .phoneNumber(m.getPhoneNumber())
                .build();

        MemberJpaEntity saved = memberJpaRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        MemberJpaEntity result = queryFactory
                .selectFrom(member)
                .where(member.id.eq(memberId), isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::mapToDomain);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        MemberJpaEntity result = queryFactory
                .selectFrom(member)
                .where(member.email.eq(email), isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::mapToDomain);
    }

    @Override
    public Member getByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new com.mariabean.reservation.global.exception.BusinessException(
                com.mariabean.reservation.global.exception.ErrorCode.ENTITY_NOT_FOUND));
    }

    @Override
    public Optional<Member> findByProviderAndProviderId(String provider, String providerId) {
        MemberJpaEntity result = queryFactory
                .selectFrom(member)
                .where(
                        member.provider.eq(provider),
                        member.providerId.eq(providerId),
                        isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::mapToDomain);
    }

    private Member mapToDomain(MemberJpaEntity entity) {
        return Member.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .name(entity.getName())
                .role(entity.getRole())
                .provider(entity.getProvider())
                .providerId(entity.getProviderId())
                .phoneNumber(entity.getPhoneNumber())
                .build();
    }
}
