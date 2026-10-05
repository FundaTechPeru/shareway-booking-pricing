package com.fundatechperu.shareway.bookingpricing.booking.application.repository;

import com.fundatechperu.shareway.bookingpricing.booking.application.model.Booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID bookingId);
}
