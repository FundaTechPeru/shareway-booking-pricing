package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

public class BookingAlreadyExistsException extends RuntimeException {

    public BookingAlreadyExistsException(String message) {
        super(message);
    }
}
