package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

public class InvalidBookingRequestException extends RuntimeException {

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
