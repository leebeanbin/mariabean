package com.mariabean.reservation.reservation.domain;

import com.mariabean.reservation.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD: Pure Domain logic tests — no Spring context needed.
 */
class ReservationTest {

    private Reservation createPendingReservation() {
        return Reservation.builder()
                .id(1L)
                .memberId(100L)
                .resourceItemId("resource-1")
                .facilityId("facility-1")
                .startTime(LocalDateTime.of(2026, 3, 7, 10, 0))
                .endTime(LocalDateTime.of(2026, 3, 7, 11, 0))
                .build();
    }

    @Test
    @DisplayName("새로 생성된 예약의 초기 상태는 PENDING이다")
    void newReservation_shouldBe_PENDING() {
        Reservation reservation = createPendingReservation();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING 상태의 예약을 CONFIRMED로 전환할 수 있다")
    void confirm_fromPending_success() {
        Reservation reservation = createPendingReservation();
        reservation.confirm();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 다시 confirm 시도하면 BusinessException 발생")
    void confirm_fromConfirmed_throwsException() {
        Reservation reservation = createPendingReservation();
        reservation.confirm();

        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PENDING 상태의 예약을 취소할 수 있다")
    void cancel_fromPending_success() {
        Reservation reservation = createPendingReservation();
        reservation.cancel();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("CONFIRMED 상태의 예약도 취소할 수 있다")
    void cancel_fromConfirmed_success() {
        Reservation reservation = createPendingReservation();
        reservation.confirm();
        reservation.cancel();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 예약을 다시 취소하면 BusinessException 발생")
    void cancel_fromCancelled_throwsException() {
        Reservation reservation = createPendingReservation();
        reservation.cancel();

        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PENDING 상태의 예약을 만료시킬 수 있다")
    void expire_fromPending_success() {
        Reservation reservation = createPendingReservation();
        reservation.expire();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("isActive는 PENDING 또는 CONFIRMED일 때만 true를 반환한다")
    void isActive_returnsCorrectly() {
        Reservation pending = createPendingReservation();
        assertThat(pending.isActive()).isTrue();

        pending.confirm();
        assertThat(pending.isActive()).isTrue();

        pending.cancel();
        assertThat(pending.isActive()).isFalse();
    }
}
