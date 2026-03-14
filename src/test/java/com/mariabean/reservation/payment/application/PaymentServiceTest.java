package com.mariabean.reservation.payment.application;

import com.mariabean.reservation.payment.application.dto.PaymentApproveRequest;
import com.mariabean.reservation.payment.application.dto.PaymentReadyRequest;
import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.application.pg.PgReadyResult;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.payment.domain.Payment;
import com.mariabean.reservation.payment.domain.PaymentProvider;
import com.mariabean.reservation.payment.domain.PaymentRepository;
import com.mariabean.reservation.payment.domain.PaymentStatus;
import com.mariabean.reservation.payment.infrastructure.external.pg.PgGatewayFactory;
import com.mariabean.reservation.auth.domain.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

        @InjectMocks
        private PaymentService paymentService;

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private com.mariabean.reservation.event.outbox.application.OutboxService outboxService;

        @Mock
        private PgGatewayFactory pgGatewayFactory;

        @Mock
        private PgGateway pgGateway;

        @Test
        @DisplayName("결제 준비(READY) 후 정상 저장된다")
        void readyPayment_success() {
                PaymentReadyRequest request = PaymentReadyRequest.builder()
                                .reservationId(100L)
                                .provider(PaymentProvider.KAKAO_PAY)
                                .amount(BigDecimal.valueOf(50000))
                                .build();

                given(pgGatewayFactory.getGateway(PaymentProvider.KAKAO_PAY)).willReturn(pgGateway);
                given(pgGateway.ready(anyLong(), any(BigDecimal.class), anyString()))
                                .willReturn(PgReadyResult.builder().tid("T123456")
                                                .redirectUrl("https://pay.kakao.com/mock").build());

                Payment saved = Payment.builder()
                                .id(1L).reservationId(100L).memberId(10L)
                                .provider(PaymentProvider.KAKAO_PAY)
                                .amount(BigDecimal.valueOf(50000))
                                .status(PaymentStatus.READY)
                                .approvalToken("T123456")
                                .build();

                given(paymentRepository.save(any(Payment.class))).willReturn(saved);

                Payment result = paymentService.readyPayment(request, 10L);
                assertThat(result.getStatus()).isEqualTo(PaymentStatus.READY);
                assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }

        @Test
        @DisplayName("결제 승인 후 Outbox 이벤트가 저장된다")
        void approvePayment_publishesOutboxEvent() {
                Payment readyPayment = Payment.builder()
                                .id(1L).reservationId(100L).memberId(10L)
                                .provider(PaymentProvider.KAKAO_PAY)
                                .amount(BigDecimal.valueOf(50000))
                                .status(PaymentStatus.READY)
                                .approvalToken("T123456")
                                .build();

                given(paymentRepository.getById(1L)).willReturn(readyPayment);
                given(pgGatewayFactory.getGateway(PaymentProvider.KAKAO_PAY)).willReturn(pgGateway);
                given(pgGateway.approve(anyString(), anyString())).willReturn("KAKAO-T123456-9999");
                given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

                PaymentApproveRequest request = PaymentApproveRequest.builder()
                                .paymentId(1L)
                                .pgToken("test-pg-token")
                                .build();

                Payment result = paymentService.approvePayment(request);

                assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                verify(outboxService).saveEvent(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("존재하지 않는 결제 승인 시 PAYMENT_NOT_FOUND 예외")
        void approvePayment_notFound() {
                given(paymentRepository.getById(999L)).willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

                PaymentApproveRequest request = PaymentApproveRequest.builder()
                                .paymentId(999L)
                                .pgToken("token")
                                .build();

                assertThatThrownBy(() -> paymentService.approvePayment(request))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("승인된 결제를 취소할 수 있다")
        void cancelPayment_success() {
                Payment approved = Payment.builder()
                                .id(1L).reservationId(100L).memberId(10L)
                                .provider(PaymentProvider.TOSS_PAY)
                                .amount(BigDecimal.valueOf(30000))
                                .status(PaymentStatus.APPROVED)
                                .transactionId("TOSS-pay-key-123")
                                .build();

                given(paymentRepository.getById(1L)).willReturn(approved);
                given(pgGatewayFactory.getGateway(PaymentProvider.TOSS_PAY)).willReturn(pgGateway);
                given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

                setSecurityContext(10L);

                Payment result = paymentService.cancelPayment(1L);
                assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        private void setSecurityContext(Long memberId) {
                UserPrincipal principal = new UserPrincipal(memberId, "test@test.com",
                                java.util.Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }
}
