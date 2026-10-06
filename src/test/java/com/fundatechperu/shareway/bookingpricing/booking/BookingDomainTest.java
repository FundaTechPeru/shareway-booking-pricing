package com.fundatechperu.shareway.bookingpricing.booking;

import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.InvalidBookingTransitionException;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BookingDomainTest {
    @Test void allowsStateSequence() {
        Booking booking = Booking.create(UUID.randomUUID(), UUID.randomUUID());
        assertThrows(InvalidBookingTransitionException.class, booking::confirm);
        booking.cancel();
        assertEquals(com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus.CANCELLED, booking.getStatus());
    }
    @Test void allowsGroupingAndCompletion() {
        Booking booking = Booking.create(UUID.randomUUID(), UUID.randomUUID());
        booking = Booking.restore(booking.getBookingId(), booking.getRequestId(), booking.getGroupId(),
                com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus.GROUPED,
                null, null, 0L, booking.getCreatedAt());
        booking.confirm(); booking.markBoarded(); booking.start(); booking.complete();
        assertEquals(com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus.COMPLETED, booking.getStatus());
    }
}
