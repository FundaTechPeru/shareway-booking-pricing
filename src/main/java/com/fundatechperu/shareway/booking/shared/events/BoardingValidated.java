package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: a passenger validated the boarding of one booking. The booking moves
 * from confirmed to boarded.
 */
public record BoardingValidated(UUID eventId, Instant occurredAt, UUID bookingId) {
}
