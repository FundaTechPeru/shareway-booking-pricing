package com.fundatechperu.shareway.bookingpricing.pricing.domain.exception;

public class InvalidFareRuleException extends RuntimeException {
    public InvalidFareRuleException(String message) {
        super(message);
    }
}
