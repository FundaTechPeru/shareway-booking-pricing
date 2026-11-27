package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event (Trip Execution): the trip of a group started. The boarded bookings of the
 * group move to in_progress. tripId is informative and may be null.
 */
public record TripStarted(UUID eventId, Instant occurredAt, UUID groupId, UUID tripId) {

    public TripStarted(UUID eventId, Instant occurredAt, UUID groupId) {
        this(eventId, occurredAt, groupId, null);
    }
}
