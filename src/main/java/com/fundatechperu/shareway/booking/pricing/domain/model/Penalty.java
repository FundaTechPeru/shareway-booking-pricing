package com.fundatechperu.shareway.booking.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Penalty charged when a booking is cancelled close to the departure. Stored per booking
 * (bookings.penalty UNIQUE): one booking can never carry two penalties.
 */
public record Penalty(UUID penaltyId, UUID bookingId, BigDecimal amount, String reason, LocalDateTime createdAt) {

    /** reason of the penalty created by a late passenger cancellation (Sprint 2, US-12). */
    public static final String LATE_CANCELLATION = "LATE_CANCELLATION";
}
