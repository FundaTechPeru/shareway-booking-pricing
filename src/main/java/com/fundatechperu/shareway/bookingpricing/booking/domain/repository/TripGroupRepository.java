package com.fundatechperu.shareway.bookingpricing.booking.domain.repository;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.TripGroup;

import java.util.Optional;
import java.util.UUID;

public interface TripGroupRepository {
    Optional<TripGroup> findById(UUID groupId);
    TripGroup save(TripGroup group);
}
