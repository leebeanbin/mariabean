package com.mariabean.reservation.payment.infrastructure.persistence;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;

import com.mariabean.reservation.payment.domain.Payment;
import com.mariabean.reservation.payment.domain.PaymentRepository;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    private static final QPaymentJpaEntity payment = QPaymentJpaEntity.paymentJpaEntity;

    private BooleanExpression isNotDeleted() {
        return payment.deletedAt.isNull();
    }

    public Payment save(Payment p) {
        return toDomain(jpaRepository.save(toEntity(p)));
    }

    public Optional<Payment> findById(Long id) {
        PaymentJpaEntity result = queryFactory
                .selectFrom(payment)
                .where(payment.id.eq(id), isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::toDomain);
    }

    @Override
    public Payment getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    public Optional<Payment> findByReservationId(Long reservationId) {
        PaymentJpaEntity result = queryFactory
                .selectFrom(payment)
                .where(payment.reservationId.eq(reservationId), isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::toDomain);
    }

    @Override
    public Payment getByReservationId(Long reservationId) {
        return findByReservationId(reservationId).orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentJpaEntity toEntity(Payment p) {
        return PaymentJpaEntity.builder()
                .id(p.getId())
                .version(p.getVersion())
                .reservationId(p.getReservationId())
                .memberId(p.getMemberId())
                .provider(p.getProvider())
                .status(p.getStatus())
                .amount(p.getAmount())
                .transactionId(p.getTransactionId())
                .approvalToken(p.getApprovalToken())
                .createdAt(p.getCreatedAt())
                .approvedAt(p.getApprovedAt())
                .build();
    }

    private Payment toDomain(PaymentJpaEntity e) {
        return Payment.builder()
                .id(e.getId())
                .version(e.getVersion())
                .reservationId(e.getReservationId())
                .memberId(e.getMemberId())
                .provider(e.getProvider())
                .status(e.getStatus())
                .amount(e.getAmount())
                .transactionId(e.getTransactionId())
                .approvalToken(e.getApprovalToken())
                .createdAt(e.getCreatedAt())
                .approvedAt(e.getApprovedAt())
                .build();
    }
}
