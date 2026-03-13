package com.mariabean.reservation.payment.application;

import com.mariabean.reservation.payment.application.dto.PaymentApproveRequest;
import com.mariabean.reservation.payment.application.dto.PaymentReadyRequest;
import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.application.pg.PgReadyResult;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.auth.application.SecurityUtils;
import com.mariabean.reservation.payment.domain.Payment;
import com.mariabean.reservation.payment.domain.PaymentRepository;
import com.mariabean.reservation.payment.infrastructure.external.pg.PgGatewayFactory;
import com.mariabean.reservation.event.kafka.PaymentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

        private final PaymentRepository paymentRepository;
        private final com.mariabean.reservation.event.outbox.application.OutboxService outboxService;
        private final PgGatewayFactory pgGatewayFactory;

        /**
         * Step 1: PG사에 결제 준비를 요청하고 사용자 결제 페이지 URL을 반환합니다.
         * 프론트엔드는 응답의 redirectUrl로 사용자를 리다이렉트합니다.
         */
        @Transactional
        public Payment readyPayment(PaymentReadyRequest request, Long memberId) {
                PgGateway gateway = pgGatewayFactory.getGateway(request.getProvider());
                String orderId = "ORDER-" + request.getReservationId() + "-" + System.currentTimeMillis();
                PgReadyResult pgResult = gateway.ready(request.getReservationId(), request.getAmount(), orderId);

                Payment payment = Payment.builder()
                                .reservationId(request.getReservationId())
                                .memberId(memberId)
                                .provider(request.getProvider())
                                .amount(request.getAmount())
                                .approvalToken(pgResult.getTid()) // tid를 저장해 두고 승인 시 사용
                                .build();

                Payment saved = paymentRepository.save(payment);
                log.info("Payment READY: id={}, reservationId={}, provider={}, redirectUrl={}",
                                saved.getId(), saved.getReservationId(), saved.getProvider(),
                                pgResult.getRedirectUrl());
                return saved;
        }

        /**
         * Step 2: PG사 콜백으로 받은 pg_token으로 결제를 최종 승인합니다.
         * 승인 후 Kafka 이벤트를 발행하여 알림 등 비동기 처리를 트리거합니다.
         */
        @Transactional
        public Payment approvePayment(PaymentApproveRequest request) {
                Payment payment = paymentRepository.getById(request.getPaymentId());
                validatePaymentOwnership(payment);

                PgGateway gateway = pgGatewayFactory.getGateway(payment.getProvider());
                String transactionId = gateway.approve(request.getPgToken(), payment.getApprovalToken());

                payment.approve(transactionId);
                Payment approved = paymentRepository.save(payment);

                // Publish domain event via Outbox Pattern for reliable delivery
                outboxService.saveEvent(
                                "PAYMENT",
                                approved.getId().toString(),
                                "PaymentApprovedEvent",
                                PaymentApprovedEvent.builder()
                                                .paymentId(approved.getId())
                                                .reservationId(approved.getReservationId())
                                                .memberId(approved.getMemberId())
                                                .amount(approved.getAmount())
                                                .provider(approved.getProvider().name())
                                                .transactionId(approved.getTransactionId())
                                                .approvedAt(approved.getApprovedAt())
                                                .build());

                log.info("Payment APPROVED: id={}, txnId={}", approved.getId(), approved.getTransactionId());
                return approved;
        }

        @Transactional
        public Payment cancelPayment(Long paymentId) {
                Payment payment = paymentRepository.getById(paymentId);

                validatePaymentOwnership(payment);

                PgGateway gateway = pgGatewayFactory.getGateway(payment.getProvider());
                gateway.cancel(payment.getTransactionId(), payment.getAmount());

                payment.cancel();
                return paymentRepository.save(payment);
        }

        @Transactional(readOnly = true)
        public Payment getPayment(Long paymentId) {
                Payment payment = paymentRepository.getById(paymentId);
                validatePaymentOwnership(payment);
                return payment;
        }

        @Transactional(readOnly = true)
        public Payment getPaymentByReservation(Long reservationId) {
                Payment payment = paymentRepository.getByReservationId(reservationId);
                validatePaymentOwnership(payment);
                return payment;
        }

        private void validatePaymentOwnership(Payment payment) {
                Long currentMemberId = SecurityUtils.getCurrentMemberId();
                if (currentMemberId == null) {
                        throw new BusinessException(ErrorCode.UNAUTHORIZED);
                }
                boolean isAdmin = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getAuthorities().stream()
                                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                                .anyMatch("ROLE_ADMIN"::equals);
                if (!payment.getMemberId().equals(currentMemberId) && !isAdmin) {
                        throw new BusinessException(ErrorCode.RESERVATION_OWNERSHIP_DENIED);
                }
        }
}
