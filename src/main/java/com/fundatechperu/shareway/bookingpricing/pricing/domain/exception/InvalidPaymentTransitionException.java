package com.fundatechperu.shareway.bookingpricing.pricing.domain.exception;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {
    public InvalidPaymentTransitionException(PaymentStatus status) {
        super("Invalid payment transition from " + status);
    }
}
