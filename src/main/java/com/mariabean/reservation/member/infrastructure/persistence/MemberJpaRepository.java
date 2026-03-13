package com.mariabean.reservation.member.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA Repository — save/delete 연산만 담당.
 * 모든 조회 쿼리는 MemberPersistenceAdapter에서 QueryDSL로 처리.
 */
public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
}
