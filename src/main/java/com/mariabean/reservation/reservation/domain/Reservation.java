package com.mariabean.reservation.reservation.domain;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pure Domain Model for Reservation.
 * Contains core business rules for reservation state transitions.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    private Long id;
    private Long version; // 낙관적 락용 — JPA @Version과 매핑됨
    private Long memberId;
    private String resourceItemId;
    private String facilityId;
    private String seatLabel;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public Reservation(Long id, Long version, Long memberId, String resourceItemId, String facilityId, String seatLabel,
            LocalDateTime startTime, LocalDateTime endTime,
            ReservationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.version = version;
        this.memberId = memberId;
        this.resourceItemId = resourceItemId;
        this.facilityId = facilityId;
        this.seatLabel = seatLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = (status != null) ? status : ReservationStatus.PENDING;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt;
    }

    // --- Domain Business Rules ---

    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_STATUS_INVALID);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == ReservationStatus.CANCELLED || this.status == ReservationStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.RESERVATION_STATUS_INVALID);
        }
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void expire() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_STATUS_INVALID);
        }
        this.status = ReservationStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == ReservationStatus.PENDING || this.status == ReservationStatus.CONFIRMED;
    }

    public void reschedule(LocalDateTime newStartTime, LocalDateTime newEndTime) {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.RESERVATION_STATUS_INVALID);
        }
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.updatedAt = LocalDateTime.now();
    }
}
