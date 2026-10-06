package com.fundatechperu.shareway.bookingpricing.booking.domain.repository;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID bookingId);

    boolean existsByRequestId(UUID requestId);
}
