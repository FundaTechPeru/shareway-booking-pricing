package com.fundatechperu.shareway.bookingpricing.booking.domain.exception;

import java.util.UUID;

public class TripGroupNotFoundException extends RuntimeException {
    public TripGroupNotFoundException(UUID groupId) {
        super("Trip group not found: " + groupId);
    }
}
