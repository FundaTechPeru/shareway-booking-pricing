package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity.TripRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataTripRequestRepository extends JpaRepository<TripRequestJpaEntity, UUID> {
}
