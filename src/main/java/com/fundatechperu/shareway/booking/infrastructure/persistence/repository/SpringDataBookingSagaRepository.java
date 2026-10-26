package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.BookingSagaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataBookingSagaRepository extends JpaRepository<BookingSagaJpaEntity, UUID> {
    Optional<BookingSagaJpaEntity> findByBookingId(UUID bookingId);
}
