package com.fundatechperu.shareway.booking.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Strategy of the passenger cancellation (Sprint 2, US-12):
 * - a cancellation closer than penaltyWindowMinutes (30) to the departure is "late" and
 *   charges a penalty;
 * - the penalty is penaltyPercent (20 %) of the estimated fare of the booking, never
 *   below minimumPenaltyAmount (1.00).
 * The departure time lives in Driver Operations & Routing and arrives through
 * {@link DepartureTimeProvider}.
 */
public class CancellationPolicy {

    private final long penaltyWindowMinutes;
    private final BigDecimal penaltyPercent;
    private final BigDecimal minimumPenaltyAmount;

    public CancellationPolicy(long penaltyWindowMinutes, BigDecimal penaltyPercent,
                              BigDecimal minimumPenaltyAmount) {
        this.penaltyWindowMinutes = penaltyWindowMinutes;
        this.penaltyPercent = penaltyPercent;
        this.minimumPenaltyAmount = minimumPenaltyAmount;
    }

    public boolean isPenaltyApplicable(Instant now, Instant departureAt) {
        return !departureAt.isAfter(now.plus(Duration.ofMinutes(penaltyWindowMinutes)));
    }

    /** 20 % of the estimated fare, HALF_UP to 2 decimals, with a floor of 1.00. */
    public BigDecimal penaltyFor(BigDecimal estimatedAmount) {
        if (estimatedAmount == null) return null;
        BigDecimal penalty = estimatedAmount.multiply(penaltyPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return penalty.max(minimumPenaltyAmount);
    }
}
