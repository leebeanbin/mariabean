package com.mariabean.reservation.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA Repository — save/delete 연산만 담당.
 * 모든 조회 쿼리는 PaymentPersistenceAdapter에서 QueryDSL로 처리.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
}
