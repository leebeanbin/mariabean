package com.mariabean.reservation.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA Repository — save/delete 연산만 담당.
 * 모든 조회 쿼리는 ReservationPersistenceAdapter에서 QueryDSL로 처리.
 */
@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {
}
