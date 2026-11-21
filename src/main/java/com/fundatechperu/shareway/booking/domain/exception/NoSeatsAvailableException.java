package com.fundatechperu.shareway.booking.domain.exception;

public class NoSeatsAvailableException extends RuntimeException {
    public NoSeatsAvailableException() {
        super("The trip has no seats available.");
    }
}
