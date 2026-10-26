package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published once when a grouped booking is confirmed by an accepted driver proposal. The
 * boarding PIN and QR token travel in clear ONLY here (the notification service needs
 * them): this service stores their hashes (BCrypt / SHA-256) and never returns or logs
 * the clear values.
 */
public record BookingConfirmed(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId,
                               UUID passengerId, UUID driverId, String boardingPin, String qrToken) {
}
