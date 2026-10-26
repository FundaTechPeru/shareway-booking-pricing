package com.fundatechperu.shareway.booking.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed event: the payment authorization of a booking failed. The booking saga
 * compensates: booking -> cancelled, seat released, saga -> compensated, BookingCancelled
 * published.
 */
public record PaymentAuthorizationFailed(UUID eventId, Instant occurredAt, UUID bookingId, String reason) {
}
