package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

public class NoSeatsAvailableException extends RuntimeException {
    public NoSeatsAvailableException() {
        super("El viaje ya no tiene asientos disponibles.");
    }
}
