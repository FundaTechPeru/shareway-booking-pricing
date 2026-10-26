package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID bookingId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRequestId(UUID requestId);

    /** Bookings of a trip group that are exactly in the given status (event handlers). */
    List<Booking> findByGroupIdAndStatus(UUID groupId, BookingStatus status);

    /** Bookings of a trip group in any status but the given one (RouteUpdated skips cancelled). */
    List<Booking> findByGroupIdAndStatusNot(UUID groupId, BookingStatus status);
}
