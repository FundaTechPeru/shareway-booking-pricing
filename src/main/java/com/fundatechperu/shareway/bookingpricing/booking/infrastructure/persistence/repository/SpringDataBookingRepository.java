package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {

    boolean existsByRequestId(UUID requestId);
}
