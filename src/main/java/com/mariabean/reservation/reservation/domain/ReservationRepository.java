package com.mariabean.reservation.reservation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
        Reservation save(Reservation reservation);

        Reservation update(Reservation reservation);

        Optional<Reservation> findById(Long id);

        Reservation getById(Long id);

        List<Reservation> findActiveByMemberId(Long memberId);

        Page<Reservation> findActiveByMemberId(Long memberId, Pageable pageable);

        Page<Reservation> findAll(Pageable pageable);

        List<Reservation> findActiveByResourceItemId(String resourceItemId);

        long countConflictingReservations(String resourceItemId, List<ReservationStatus> statuses,
                        LocalDateTime start, LocalDateTime end);

        long countActiveBeforeCreatedAt(String resourceItemId, List<ReservationStatus> statuses,
                        LocalDateTime createdAt);

        List<Reservation> findPendingBefore(List<ReservationStatus> statuses, LocalDateTime before);
}
