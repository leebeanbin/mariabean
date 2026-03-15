package com.mariabean.reservation.payment.application;

import com.mariabean.reservation.payment.application.dto.PaymentApproveRequest;
import com.mariabean.reservation.payment.application.dto.PaymentReadyRequest;
import com.mariabean.reservation.payment.application.pg.PgGateway;
import com.mariabean.reservation.payment.application.pg.PgReadyResult;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.auth.application.SecurityUtils;
import com.mariabean.reservation.facility.domain.ResourceItem;
import com.mariabean.reservation.facility.domain.ResourceItemRepository;
import com.mariabean.reservation.payment.domain.Payment;
import com.mariabean.reservation.payment.domain.PaymentRepository;
import com.mariabean.reservation.payment.infrastructure.external.pg.PgGatewayFactory;
import com.mariabean.reservation.reservation.domain.Reservation;
import com.mariabean.reservation.reservation.domain.ReservationRepository;
import com.mariabean.reservation.event.kafka.PaymentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

        private final PaymentRepository paymentRepository;
        private final ReservationRepository reservationRepository;
        private final ResourceItemRepository resourceItemRepository;
        private final com.mariabean.reservation.event.outbox.application.OutboxService outboxService;
        private final PgGatewayFactory pgGatewayFactory;

        /**
         * Step 1: PG사에 결제 준비를 요청하고 사용자 결제 페이지 URL을 반환합니다.
         * 프론트엔드는 응답의 redirectUrl로 사용자를 리다이렉트합니다.
         */
        @Transactional
        public Payment readyPayment(PaymentReadyRequest request, Long memberId) {
                if (paymentRepository.existsActivePayment(request.getReservationId())) {
                        throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
                }

                // 서버 측 금액 검증: 클라이언트가 보낸 amount와 서버 계산 금액 비교
                validatePaymentAmount(request.getReservationId(), request.getAmount());

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

        /**
         * 예약 기간과 리소스 시간당 요금으로 서버 측 결제 금액을 계산하고 클라이언트 요청 값과 비교.
         * ResourceItem.customAttributes에 "pricePerHour" 키가 없으면 검증을 건너뜀 (무료 리소스).
         * 허용 오차: ±1원 (반올림 차이 허용).
         */
        private void validatePaymentAmount(Long reservationId, BigDecimal clientAmount) {
                Reservation reservation = reservationRepository.getById(reservationId);
                ResourceItem resource = resourceItemRepository.getById(reservation.getResourceItemId());

                Object pricePerHourRaw = resource.getCustomAttributes().get("pricePerHour");
                if (pricePerHourRaw == null) {
                        return; // 가격 정보 없는 경우 검증 skip (무료 리소스 또는 별도 정책)
                }

                BigDecimal pricePerHour;
                try {
                        pricePerHour = new BigDecimal(pricePerHourRaw.toString());
                } catch (NumberFormatException e) {
                        log.warn("[Payment] pricePerHour 파싱 실패, 검증 skip: resourceId={}", resource.getId());
                        return;
                }

                long minutes = Duration.between(reservation.getStartTime(), reservation.getEndTime()).toMinutes();
                BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                BigDecimal expectedAmount = pricePerHour.multiply(hours).setScale(0, RoundingMode.HALF_UP);

                if (clientAmount.setScale(0, RoundingMode.HALF_UP).subtract(expectedAmount).abs()
                        .compareTo(BigDecimal.ONE) > 0) {
                        log.warn("[Payment] 금액 불일치: reservationId={}, clientAmount={}, expectedAmount={}",
                                reservationId, clientAmount, expectedAmount);
                        throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
                }
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
