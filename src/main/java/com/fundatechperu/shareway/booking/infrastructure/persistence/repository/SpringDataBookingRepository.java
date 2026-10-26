package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {

    Optional<BookingJpaEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRequestId(UUID requestId);
}
