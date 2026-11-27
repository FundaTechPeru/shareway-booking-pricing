package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event (Trip Execution): the trip of a group was cancelled. Its active bookings are
 * cancelled without penalty (NO_DRIVER_AVAILABLE), their seats released and sagas compensated.
 */
public record TripCancelled(UUID eventId, Instant occurredAt, UUID groupId, UUID tripId, String reason) {
}
