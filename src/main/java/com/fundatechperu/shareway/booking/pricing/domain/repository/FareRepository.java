package com.fundatechperu.shareway.booking.pricing.domain.repository;

import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;

import java.util.Optional;
import java.util.UUID;

public interface FareRepository {
    Fare save(Fare fare);
    Optional<Fare> findByBookingIdAndFareType(UUID bookingId, String fareType);
}
