package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the same local transaction that holds the seat: a seat is now
 * reserved for the booking and the payment authorization flow may start.
 */
public record SeatHeld(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId, UUID passengerId) {
}
