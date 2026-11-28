package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event (Trip Execution): a passenger validated the boarding of one booking. The
 * booking moves from confirmed to boarded. tripId is informative and may be null.
 */
public record BoardingValidated(UUID eventId, Instant occurredAt, UUID bookingId, UUID tripId) {

    public BoardingValidated(UUID eventId, Instant occurredAt, UUID bookingId) {
        this(eventId, occurredAt, bookingId, null);
    }
}
