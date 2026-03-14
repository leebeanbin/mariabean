package com.mariabean.reservation.reservation.application;

import com.mariabean.reservation.reservation.application.dto.ReservationCreateRequest;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationStatus;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.mariabean.reservation.event.outbox.application.OutboxService;
import com.mariabean.reservation.auth.domain.UserPrincipal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

        @InjectMocks
        private ReservationService reservationService;

        @Mock
        private ReservationRepository reservationRepository;

        @Mock
        private RedissonClient redissonClient;

        @Mock
        private RLock rLock;

        @Mock
        private OutboxService outboxService;

        @Test
        @DisplayName("예약 생성 시 분산 락 획득 후 정상 저장된다")
        void createReservation_success() throws InterruptedException {
                // given
                ReservationCreateRequest request = ReservationCreateRequest.builder()
                                .resourceItemId("res-1")
                                .facilityId("fac-1")
                                .startTime(LocalDateTime.of(2026, 3, 7, 10, 0))
                                .endTime(LocalDateTime.of(2026, 3, 7, 11, 0))
                                .build();

                given(redissonClient.getLock(anyString())).willReturn(rLock);
                given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
                given(rLock.isHeldByCurrentThread()).willReturn(true);

                Reservation savedReservation = Reservation.builder()
                                .id(1L).memberId(100L)
                                .resourceItemId("res-1").facilityId("fac-1")
                                .startTime(request.getStartTime()).endTime(request.getEndTime())
                                .status(ReservationStatus.PENDING)
                                .build();
                given(reservationRepository.countConflictingReservations(anyString(), anyList(), any(), any()))
                                .willReturn(0L);
                given(reservationRepository.save(any(Reservation.class))).willReturn(savedReservation);

                // when
                Reservation result = reservationService.createReservation(request, 100L);

                // then
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
                verify(rLock).unlock();
        }

        @Test
        @DisplayName("분산 락 획득 실패 시 RESERVATION_LOCK_FAILED 예외 발생")
        void createReservation_lockFailed() throws InterruptedException {
                // given
                ReservationCreateRequest request = ReservationCreateRequest.builder()
                                .resourceItemId("res-1")
                                .facilityId("fac-1")
                                .startTime(LocalDateTime.of(2026, 3, 7, 10, 0))
                                .endTime(LocalDateTime.of(2026, 3, 7, 11, 0))
                                .build();

                given(redissonClient.getLock(anyString())).willReturn(rLock);
                given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

                // when & then
                assertThatThrownBy(() -> reservationService.createReservation(request, 100L))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_LOCK_FAILED);
        }

        @Test
        @DisplayName("존재하는 예약을 정상적으로 확정할 수 있다")
        void confirmReservation_success() {
                // given
                Reservation pendingReservation = Reservation.builder()
                                .id(1L).memberId(100L).resourceItemId("res-1").facilityId("fac-1")
                                .startTime(LocalDateTime.now()).endTime(LocalDateTime.now().plusHours(1))
                                .status(ReservationStatus.PENDING)
                                .build();

                given(reservationRepository.getById(1L)).willReturn(pendingReservation);
                given(reservationRepository.update(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

                // when
                Reservation result = reservationService.confirmReservation(1L);

                // then
                assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("존재하지 않는 예약 확정 시 RESERVATION_NOT_FOUND 예외")
        void confirmReservation_notFound() {
                given(reservationRepository.getById(999L))
                                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

                assertThatThrownBy(() -> reservationService.confirmReservation(999L))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
        }

        @Test
        @DisplayName("예약 취소가 정상적으로 처리된다")
        void cancelReservation_success() {
                Reservation reservation = Reservation.builder()
                                .id(1L).memberId(100L).resourceItemId("res-1").facilityId("fac-1")
                                .startTime(LocalDateTime.now()).endTime(LocalDateTime.now().plusHours(1))
                                .status(ReservationStatus.PENDING)
                                .build();

                given(reservationRepository.getById(1L)).willReturn(reservation);
                given(reservationRepository.update(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

                setSecurityContext(100L);

                Reservation result = reservationService.cancelReservation(1L);
                assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        private void setSecurityContext(Long memberId) {
                UserPrincipal principal = new UserPrincipal(memberId, "test@test.com",
                                java.util.Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }
}
