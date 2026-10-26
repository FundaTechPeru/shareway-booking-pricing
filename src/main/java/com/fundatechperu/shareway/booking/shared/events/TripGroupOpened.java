package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: a new trip group opened in Driver Operations & Routing. This service
 * creates its seat_inventory (no foreign keys across services: group_id is a plain UUID).
 */
public record TripGroupOpened(UUID eventId, Instant occurredAt, UUID groupId, int capacity) {
}
