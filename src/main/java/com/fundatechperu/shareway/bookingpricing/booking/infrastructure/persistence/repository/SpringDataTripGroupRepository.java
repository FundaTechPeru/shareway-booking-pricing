package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity.TripGroupJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataTripGroupRepository extends JpaRepository<TripGroupJpaEntity, UUID> {
}
