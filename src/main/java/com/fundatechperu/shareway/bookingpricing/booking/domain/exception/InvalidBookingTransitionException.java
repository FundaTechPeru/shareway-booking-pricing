package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus;

public class InvalidBookingTransitionException extends RuntimeException {
    public InvalidBookingTransitionException(BookingStatus current, BookingStatus target) {
        super("Invalid booking transition from " + current + " to " + target);
    }
}
