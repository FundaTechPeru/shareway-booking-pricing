package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

public class ConcurrentReservationException extends RuntimeException {
    public ConcurrentReservationException() {
        super("La reserva no pudo completarse por concurrencia; inténtelo nuevamente.");
    }
}
