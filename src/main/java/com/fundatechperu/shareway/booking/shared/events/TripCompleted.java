package com.fundatechperu.shareway.booking.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: a trip group finished. The in_progress bookings of the group move to
 * completed and the final fare is priced with the REAL distance carried by this event.
 */
public record TripCompleted(UUID eventId, Instant occurredAt, UUID groupId, BigDecimal distanceKm) {
}
