package com.mariabean.reservation.reservation.application;

import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PENDING 상태로 일정 시간(30분) 경과한 예약을 자동 만료시키는 스케줄러.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;

    private static final int EXPIRY_MINUTES = 30;

    @Scheduled(fixedRate = 60_000) // 1분마다 실행
    @Transactional
    public void expirePendingReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        List<ReservationStatus> pendingOnly = List.of(ReservationStatus.PENDING);

        List<Reservation> expired = reservationRepository
                .findPendingBefore(pendingOnly, cutoff);

        int count = 0;
        for (Reservation reservation : expired) {
            try {
                reservation.expire();
                reservationRepository.update(reservation);
                count++;
            } catch (Exception e) {
                log.warn("[ExpiryScheduler] Failed to expire reservation [{}]: {}",
                        reservation.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("[ExpiryScheduler] Expired {} pending reservations older than {} minutes",
                    count, EXPIRY_MINUTES);
        }
    }
}
