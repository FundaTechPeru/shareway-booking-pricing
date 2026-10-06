package com.fundatechperu.shareway.bookingpricing.pricing.domain.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(UUID paymentId);
    Optional<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findActiveByBookingId(UUID bookingId);
}
