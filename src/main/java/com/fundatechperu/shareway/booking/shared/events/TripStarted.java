package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: the trip of a group started. The boarded bookings of the group move to
 * in_progress.
 */
public record TripStarted(UUID eventId, Instant occurredAt, UUID groupId) {
}
