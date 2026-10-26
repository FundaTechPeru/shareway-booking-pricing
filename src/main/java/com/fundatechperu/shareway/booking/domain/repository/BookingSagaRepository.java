package com.fundatechperu.shareway.booking.domain.repository;

import com.fundatechperu.shareway.booking.domain.model.BookingSaga;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingSagaRepository {
    Optional<BookingSaga> findById(UUID sagaId);
    Optional<BookingSaga> findByBookingId(UUID bookingId);
    BookingSaga save(BookingSaga saga);

    /** Running sagas whose deadline already passed: the timeout job compensates them. */
    List<BookingSaga> findByStatusAndDeadlineAtBefore(BookingSaga.Status status, LocalDateTime deadlineAt);
}
