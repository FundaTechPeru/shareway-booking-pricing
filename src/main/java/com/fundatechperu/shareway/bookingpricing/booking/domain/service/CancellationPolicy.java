package com.fundatechperu.shareway.bookingpricing.booking.domain.service;

import java.time.Duration;
import java.time.Instant;

public class CancellationPolicy {
    private final long penaltyWindowMinutes;
    public CancellationPolicy(long penaltyWindowMinutes) { this.penaltyWindowMinutes = penaltyWindowMinutes; }
    public boolean isPenaltyApplicable(Instant now, Instant departureAt) {
        return !departureAt.isAfter(now.plus(Duration.ofMinutes(penaltyWindowMinutes)));
    }
}
