package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.BookingSaga;

import java.util.Optional;
import java.util.UUID;

public interface BookingSagaRepository {
    Optional<BookingSaga> findById(UUID sagaId);
    Optional<BookingSaga> findByBookingId(UUID bookingId);
    BookingSaga save(BookingSaga saga);
}
