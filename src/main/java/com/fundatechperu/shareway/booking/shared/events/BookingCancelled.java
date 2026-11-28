package com.fundatechperu.shareway.booking.shared.events;

import com.fundatechperu.shareway.booking.domain.model.CancellationReason;

import java.time.Instant;
import java.util.UUID;

/** Published when a booking is cancelled, with the reason of the cancellation. */
public record BookingCancelled(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId,
                               UUID payerId, boolean penaltyApplicable, CancellationReason reason) {
}
