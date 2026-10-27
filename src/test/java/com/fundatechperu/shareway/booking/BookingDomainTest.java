package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingTransitionException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BookingDomainTest {
    @Test void allowsStateSequence() {
        Booking booking = Booking.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        assertThrows(InvalidBookingTransitionException.class, booking::confirm);
        booking.cancel();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }
    @Test void allowsGroupingAndCompletion() {
        Booking booking = Booking.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        booking = Booking.restore(booking.getBookingId(), booking.getRequestId(), booking.getGroupId(),
                booking.getPassengerId(), booking.getIdempotencyKey(), BookingStatus.GROUPED,
                null, null, 0L, booking.getCreatedAt());
        booking.confirm(); booking.markBoarded(); booking.start(); booking.complete();
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }
}
