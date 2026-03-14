package com.mariabean.reservation.reservation.application;

import com.mariabean.reservation.reservation.application.dto.ReservationCreateRequest;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

/**
 * 동시성 시뮬레이션 테스트: 여러 스레드가 동시에 같은 자원 예약을 시도할 때
 * Redisson 분산 락이 정상 작동하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationConcurrencyTest {

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("동시 10개 스레드 예약 요청 중 락 획득 실패 시 BusinessException 발생 수 검증")
    void concurrentReservation_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // First call succeeds, rest fail to acquire lock
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .willReturn(true)    // first call succeeds
                .willReturn(false);  // all subsequent calls fail
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        Reservation saved = Reservation.builder()
                .id(1L).memberId(1L).resourceItemId("res-1").facilityId("fac-1")
                .startTime(LocalDateTime.of(2026, 3, 7, 10, 0))
                .endTime(LocalDateTime.of(2026, 3, 7, 11, 0))
                .status(ReservationStatus.PENDING)
                .build();
        given(reservationRepository.countConflictingReservations(anyString(), anyList(), any(), any())).willReturn(0L);
        given(reservationRepository.save(any(Reservation.class))).willReturn(saved);

        ReservationCreateRequest request = ReservationCreateRequest.builder()
                .resourceItemId("res-1")
                .facilityId("fac-1")
                .startTime(LocalDateTime.of(2026, 3, 7, 10, 0))
                .endTime(LocalDateTime.of(2026, 3, 7, 11, 0))
                .build();

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    reservationService.createReservation(request, memberId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // At most 1 should succeed (the first lock holder), rest should fail
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
        assertThat(failCount.get()).isGreaterThanOrEqualTo(threadCount - 1);
    }
}
