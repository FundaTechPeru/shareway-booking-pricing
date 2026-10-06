package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {
    Optional<PaymentJpaEntity> findByBookingId(UUID bookingId);
    Optional<PaymentJpaEntity> findByBookingIdAndStatusIn(UUID bookingId, java.util.Collection<PaymentStatus> statuses);
}
