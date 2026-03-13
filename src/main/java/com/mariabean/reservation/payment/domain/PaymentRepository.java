package com.mariabean.reservation.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Payment getById(Long id);

    Optional<Payment> findByReservationId(Long reservationId);

    Payment getByReservationId(Long reservationId);
}
