package com.fundatechperu.shareway.booking.pricing.domain.exception;

public class InvalidFareRuleException extends RuntimeException {
    public InvalidFareRuleException(String message) {
        super(message);
    }
}
