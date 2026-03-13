package com.mariabean.reservation.reservation.application;

import com.mariabean.reservation.reservation.application.dto.AvailabilityResponse;
import com.mariabean.reservation.reservation.application.dto.ReservationCreateRequest;
import com.mariabean.reservation.reservation.application.dto.ReservationRescheduleRequest;
import com.mariabean.reservation.reservation.application.dto.WaitingInfoResponse;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.auth.application.SecurityUtils;
import com.mariabean.reservation.facility.domain.ResourceItem;
import com.mariabean.reservation.facility.domain.ResourceItemRepository;
import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import com.mariabean.reservation.event.kafka.ReservationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final RedissonClient redissonClient;
    private final com.mariabean.reservation.event.outbox.application.OutboxService outboxService;

    private static final String LOCK_PREFIX = "reservation:lock:";
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;
    private static final int DEFAULT_CAPACITY = 1;

    @Transactional
    public Reservation createReservation(ReservationCreateRequest request, Long memberId) {
        String lockKey = LOCK_PREFIX + request.getResourceItemId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.RESERVATION_LOCK_FAILED);
        }

        if (!acquired) {
            throw new BusinessException(ErrorCode.RESERVATION_LOCK_FAILED);
        }

        try {
            // Time slot alignment validation
            validateTimeSlot(request.getStartTime(), request.getEndTime());
            List<ReservationStatus> activeStatuses = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
            long conflictCount = reservationRepository.countConflictingReservations(
                    request.getResourceItemId(), activeStatuses, request.getStartTime(), request.getEndTime());

            if (conflictCount >= DEFAULT_CAPACITY) {
                throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
            }

            Reservation reservation = Reservation.builder()
                    .memberId(memberId)
                    .resourceItemId(request.getResourceItemId())
                    .facilityId(request.getFacilityId())
                    .seatLabel(request.getSeatLabel())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .build();

            Reservation saved = reservationRepository.save(reservation);
            log.info("Reservation [{}] created for resource [{}] by member [{}]",
                    saved.getId(), saved.getResourceItemId(), saved.getMemberId());

            outboxService.saveEvent(
                    "RESERVATION",
                    saved.getId().toString(),
                    "ReservationCreatedEvent",
                    ReservationEvent.builder()
                            .reservationId(saved.getId())
                            .memberId(saved.getMemberId())
                            .resourceItemId(saved.getResourceItemId())
                            .facilityId(saved.getFacilityId())
                            .seatLabel(saved.getSeatLabel())
                            .eventType(ReservationEvent.EVENT_CREATED)
                            .occurredAt(LocalDateTime.now())
                            .build());

            return saved;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = reservationRepository.getById(reservationId);
        reservation.confirm();
        return reservationRepository.update(reservation);
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.getById(reservationId);
        validateOwnership(reservation);
        reservation.cancel();
        Reservation updated = reservationRepository.update(reservation);

        outboxService.saveEvent(
                "RESERVATION",
                updated.getId().toString(),
                "ReservationCancelledEvent",
                ReservationEvent.builder()
                        .reservationId(updated.getId())
                        .memberId(updated.getMemberId())
                        .resourceItemId(updated.getResourceItemId())
                        .facilityId(updated.getFacilityId())
                        .seatLabel(updated.getSeatLabel())
                        .eventType(ReservationEvent.EVENT_CANCELLED)
                        .occurredAt(LocalDateTime.now())
                        .build());

        return updated;
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(Long reservationId) {
        Reservation reservation = reservationRepository.getById(reservationId);
        validateOwnership(reservation);
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<Reservation> getMyReservations(Long memberId) {
        return reservationRepository.findActiveByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Reservation> getMyReservations(Long memberId,
            org.springframework.data.domain.Pageable pageable) {
        return reservationRepository.findActiveByMemberId(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Reservation> getAllReservations(
            org.springframework.data.domain.Pageable pageable) {
        return reservationRepository.findAll(pageable);
    }

    /**
     * 관리자 전용: 예약 시간 변경 (Reschedule).
     * 분산 락 + 시간 충돌 검증 수행.
     */
    @Transactional
    public Reservation rescheduleReservation(Long reservationId, ReservationRescheduleRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        validateTimeSlot(request.getNewStartTime(), request.getNewEndTime());

        String lockKey = LOCK_PREFIX + reservation.getResourceItemId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.RESERVATION_LOCK_FAILED);
        }

        if (!acquired) {
            throw new BusinessException(ErrorCode.RESERVATION_LOCK_FAILED);
        }

        try {
            // Check conflicts excluding current reservation's own time
            List<ReservationStatus> activeStatuses = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
            long conflictCount = reservationRepository.countConflictingReservations(
                    reservation.getResourceItemId(), activeStatuses,
                    request.getNewStartTime(), request.getNewEndTime());

            // If only conflict is the reservation itself, it's OK (count will be 1)
            // If the reservation's current time overlaps with the new time, it counts
            // itself
            // So we allow conflictCount <= 1 (itself)
            long maxAllowed = reservation.isActive() ? 1 : 0;
            if (conflictCount > maxAllowed) {
                throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
            }

            reservation.reschedule(request.getNewStartTime(), request.getNewEndTime());
            Reservation updated = reservationRepository.update(reservation);
            log.info("Reservation [{}] rescheduled to {} ~ {} by admin",
                    updated.getId(), updated.getStartTime(), updated.getEndTime());
            return updated;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ─── Private Helpers ─────────────────────────────────────

    /**
     * 30분 단위 정렬 검증: 분(minute)은 0 또는 30, 초/나노초는 0이어야 함.
     * endTime > startTime 검증 포함.
     */
    private void validateTimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime.isAfter(endTime) || startTime.isEqual(endTime)) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_MISALIGNED);
        }
        if (!isAligned(startTime) || !isAligned(endTime)) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_MISALIGNED);
        }
    }

    private boolean isAligned(LocalDateTime time) {
        int minute = time.getMinute();
        return (minute == 0 || minute == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    /**
     * 예약 소유자 또는 ADMIN 역할인지 검증.
     */
    private void validateOwnership(Reservation reservation) {
        Long currentMemberId = SecurityUtils.getCurrentMemberId();
        if (currentMemberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!reservation.getMemberId().equals(currentMemberId) && !isAdmin()) {
            throw new BusinessException(ErrorCode.RESERVATION_OWNERSHIP_DENIED);
        }
    }

    private boolean isAdmin() {
        return SecurityUtils.getCurrentMemberId() != null
                && org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * 공개 API: 특정 날짜의 리소스 가용 시간 슬롯 조회 (30분 단위, 09:00~21:00).
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(String resourceItemId, LocalDate date) {
        LocalDateTime startOfDay = date.atTime(9, 0);
        LocalDateTime endOfDay = date.atTime(21, 0);

        List<ReservationStatus> activeStatuses = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        List<Reservation> existing = reservationRepository.findActiveByResourceItemId(resourceItemId)
                .stream()
                .filter(r -> activeStatuses.contains(r.getStatus()))
                .filter(r -> !r.getEndTime().isBefore(startOfDay) && !r.getStartTime().isAfter(endOfDay))
                .toList();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<AvailabilityResponse.TimeSlot> slots = new ArrayList<>();

        LocalDateTime cursor = startOfDay;
        while (cursor.isBefore(endOfDay)) {
            LocalDateTime slotEnd = cursor.plusMinutes(30);
            final LocalDateTime slotStart = cursor;
            // overlap 조건: r.start < slotEnd AND r.end > slotStart
            boolean occupied = existing.stream().anyMatch(r ->
                    r.getStartTime().isBefore(slotEnd) && r.getEndTime().isAfter(slotStart)
            );
            slots.add(new AvailabilityResponse.TimeSlot(
                    slotStart.format(fmt),
                    slotEnd.format(fmt),
                    !occupied
            ));
            cursor = slotEnd;
        }

        return new AvailabilityResponse(resourceItemId, date.toString(), slots);
    }

    /**
     * 관리자 전용: 해당 리소스의 대기열 중 가장 오래된 PENDING 예약을 즉시 확정 (일찍 부르기).
     */
    @Transactional
    public Reservation callNext(String resourceItemId) {
        List<Reservation> pending = reservationRepository.findActiveByResourceItemId(resourceItemId)
                .stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .sorted(Comparator.comparing(Reservation::getCreatedAt))
                .toList();

        if (pending.isEmpty()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        Reservation next = pending.get(0);
        next.confirm();
        Reservation updated = reservationRepository.update(next);

        log.info("[callNext] Reservation [{}] confirmed early for resource [{}]",
                updated.getId(), resourceItemId);

        outboxService.saveEvent(
                "RESERVATION",
                updated.getId().toString(),
                "ReservationConfirmedEvent",
                ReservationEvent.builder()
                        .reservationId(updated.getId())
                        .memberId(updated.getMemberId())
                        .resourceItemId(updated.getResourceItemId())
                        .facilityId(updated.getFacilityId())
                        .seatLabel(updated.getSeatLabel())
                        .eventType(ReservationEvent.EVENT_CONFIRMED)
                        .occurredAt(LocalDateTime.now())
                        .build());

        return updated;
    }

    @Transactional
    public void markAsExpired(Long reservationId) {
        Reservation reservation = reservationRepository.getById(reservationId);
        reservation.expire();
        reservationRepository.update(reservation);
    }

    @Transactional(readOnly = true)
    public WaitingInfoResponse getWaitingInfo(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        validateOwnership(reservation);

        List<ReservationStatus> activeStatuses = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

        // Queue position = number of active reservations created before this one + 1
        long aheadCount = reservationRepository.countActiveBeforeCreatedAt(
                reservation.getResourceItemId(), activeStatuses, reservation.getCreatedAt());
        int queuePosition = (int) aheadCount + 1;

        // Total active reservations for this resource
        List<Reservation> activeList = reservationRepository
                .findActiveByResourceItemId(reservation.getResourceItemId());

        // Get resource's admin-set estimated wait time
        Integer estimatedWait = resourceItemRepository.findById(reservation.getResourceItemId())
                .map(ResourceItem::getEstimatedWaitMinutes)
                .orElse(null);

        Integer totalEstimatedWait = (estimatedWait != null) ? estimatedWait * ((int) aheadCount) : null;

        return WaitingInfoResponse.builder()
                .reservationId(reservationId)
                .queuePosition(queuePosition)
                .estimatedWaitMinutes(totalEstimatedWait)
                .totalActiveReservations(activeList.size())
                .build();
    }
}
