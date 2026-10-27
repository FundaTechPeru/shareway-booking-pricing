package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.BookingSagaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataBookingSagaRepository extends JpaRepository<BookingSagaJpaEntity, UUID> {
    Optional<BookingSagaJpaEntity> findByBookingId(UUID bookingId);

    /** Status is stored in lowercase (running | completed | compensated). */
    List<BookingSagaJpaEntity> findByStatusAndDeadlineAtBefore(String status, LocalDateTime deadlineAt);
}
