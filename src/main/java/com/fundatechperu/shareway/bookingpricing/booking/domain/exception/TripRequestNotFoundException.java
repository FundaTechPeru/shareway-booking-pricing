package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

import java.util.UUID;

public class TripRequestNotFoundException extends RuntimeException {
    public TripRequestNotFoundException(UUID requestId) {
        super("Trip request not found: " + requestId);
    }
}
