package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.Booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID bookingId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRequestId(UUID requestId);
}
