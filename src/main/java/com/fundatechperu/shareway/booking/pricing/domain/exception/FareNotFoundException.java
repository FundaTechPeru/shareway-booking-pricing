package com.fundatechperu.shareway.booking.pricing.domain.exception;

public class FareNotFoundException extends RuntimeException {

    public FareNotFoundException(Long fareId) {
        super("Fare not found: " + fareId);
    }

    public FareNotFoundException(String message) {
        super(message);
    }
}
