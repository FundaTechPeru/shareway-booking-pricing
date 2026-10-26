package com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.pricing.infrastructure.persistence.entity.FareJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataFareRepository extends JpaRepository<FareJpaEntity, UUID> {
    Optional<FareJpaEntity> findByBookingIdAndFareType(UUID bookingId, String fareType);
}
