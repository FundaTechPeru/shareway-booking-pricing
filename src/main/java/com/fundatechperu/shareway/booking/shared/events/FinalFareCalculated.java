package com.fundatechperu.shareway.booking.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a booking closes: the trip ended and the FINAL fare was persisted with
 * the real distance and the fare rule valid that day. ruleVersion identifies the rule
 * that produced the amount (same value the fares row references through rule_id).
 */
public record FinalFareCalculated(UUID eventId, Instant occurredAt, UUID bookingId,
                                  BigDecimal amount, String currency, int ruleVersion) {
}
