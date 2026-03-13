package com.mariabean.reservation.reservation.infrastructure.persistence;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    private static final QReservationJpaEntity reservation = QReservationJpaEntity.reservationJpaEntity;

    // --- 공통 조건 ---

    private BooleanExpression isNotDeleted() {
        return reservation.deletedAt.isNull();
    }

    private BooleanExpression hasActiveStatus() {
        return reservation.status.in(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    }

    // --- Repository 구현 ---

    @Override
    public Reservation save(Reservation r) {
        return toDomain(jpaRepository.save(toEntity(r)));
    }

    @Override
    public Reservation update(Reservation r) {
        return toDomain(jpaRepository.save(toEntity(r)));
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        ReservationJpaEntity result = queryFactory
                .selectFrom(reservation)
                .where(reservation.id.eq(id), isNotDeleted())
                .fetchOne();
        return Optional.ofNullable(result).map(this::toDomain);
    }

    @Override
    public Reservation getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    @Override
    public List<Reservation> findActiveByMemberId(Long memberId) {
        return queryFactory
                .selectFrom(reservation)
                .where(reservation.memberId.eq(memberId), hasActiveStatus(), isNotDeleted())
                .fetch()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<Reservation> findActiveByMemberId(Long memberId, Pageable pageable) {
        List<Reservation> content = queryFactory
                .selectFrom(reservation)
                .where(reservation.memberId.eq(memberId), hasActiveStatus(), isNotDeleted())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream().map(this::toDomain).collect(Collectors.toList());

        Long total = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(reservation.memberId.eq(memberId), hasActiveStatus(), isNotDeleted())
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Reservation> findAll(Pageable pageable) {
        List<Reservation> content = queryFactory
                .selectFrom(reservation)
                .where(isNotDeleted())
                .orderBy(reservation.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream().map(this::toDomain).collect(Collectors.toList());

        Long total = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(isNotDeleted())
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Reservation> findActiveByResourceItemId(String resourceItemId) {
        return queryFactory
                .selectFrom(reservation)
                .where(reservation.resourceItemId.eq(resourceItemId), hasActiveStatus(), isNotDeleted())
                .fetch()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countConflictingReservations(String resourceItemId, List<ReservationStatus> statuses,
            LocalDateTime start, LocalDateTime end) {
        Long result = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(
                        reservation.resourceItemId.eq(resourceItemId),
                        reservation.status.in(statuses),
                        reservation.startTime.lt(end),
                        reservation.endTime.gt(start),
                        isNotDeleted())
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public long countActiveBeforeCreatedAt(String resourceItemId, List<ReservationStatus> statuses,
            LocalDateTime createdAt) {
        Long result = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(
                        reservation.resourceItemId.eq(resourceItemId),
                        reservation.status.in(statuses),
                        reservation.createdAt.lt(createdAt),
                        isNotDeleted())
                .fetchOne();
        return result != null ? result : 0L;
    }

    @Override
    public List<Reservation> findPendingBefore(List<ReservationStatus> statuses, LocalDateTime before) {
        return queryFactory
                .selectFrom(reservation)
                .where(
                        reservation.status.in(statuses),
                        reservation.createdAt.lt(before),
                        isNotDeleted())
                .fetch()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    // --- Mapping ---

    private ReservationJpaEntity toEntity(Reservation r) {
        return ReservationJpaEntity.builder()
                .id(r.getId())
                .version(r.getVersion())
                .memberId(r.getMemberId())
                .resourceItemId(r.getResourceItemId())
                .facilityId(r.getFacilityId())
                .seatLabel(r.getSeatLabel())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private Reservation toDomain(ReservationJpaEntity e) {
        return Reservation.builder()
                .id(e.getId())
                .version(e.getVersion())
                .memberId(e.getMemberId())
                .resourceItemId(e.getResourceItemId())
                .facilityId(e.getFacilityId())
                .seatLabel(e.getSeatLabel())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
