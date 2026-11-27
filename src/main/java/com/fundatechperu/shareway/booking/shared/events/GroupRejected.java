package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event (Trip Planning): the driver rejected the group. Its active bookings are
 * cancelled without penalty (NO_DRIVER_AVAILABLE), their seats released and sagas compensated.
 */
public record GroupRejected(UUID eventId, Instant occurredAt, UUID groupId, UUID driverId) {
}
