package com.fundatechperu.shareway.booking.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a booking enters the saga: the payment authorization of the estimated
 * fare must be requested. `zone` was dropped with the old schema; distance comes from
 * Driver Operations & Routing.
 */
public record PaymentAuthorizationRequested(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId,
                                            UUID payerId, LocalDate date, BigDecimal distanceKm,
                                            int passengers) {
}
