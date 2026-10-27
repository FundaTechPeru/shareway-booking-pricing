package com.fundatechperu.shareway.booking.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: the route distance of a trip group changed. The estimated fare of the
 * group's bookings is recalculated with the rule valid today and the fares row
 * (fare_type estimated) is updated in place. No event is published in return.
 */
public record RouteUpdated(UUID eventId, Instant occurredAt, UUID groupId, BigDecimal distanceKm) {
}
