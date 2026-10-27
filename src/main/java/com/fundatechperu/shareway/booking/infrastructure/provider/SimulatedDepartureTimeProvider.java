package com.fundatechperu.shareway.booking.infrastructure.provider;

import com.fundatechperu.shareway.booking.domain.service.DepartureTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Simulated {@link DepartureTimeProvider} used until Driver Operations & Routing exposes
 * the real departure of a trip group: every group departs offsetMinutes (default 120)
 * after "now". The offset comes from the BOOKING_DEPARTURE_SIMULATED_OFFSET_MINUTES
 * environment variable and can be changed at runtime to exercise the 30-minute penalty
 * window (the setter exists for manual and automated simulation only).
 */
@Component
public class SimulatedDepartureTimeProvider implements DepartureTimeProvider {

    private final Clock clock;
    private volatile long offsetMinutes;

    public SimulatedDepartureTimeProvider(Clock clock,
                                          @Value("${booking.departure.simulated-offset-minutes:120}")
                                          long offsetMinutes) {
        this.clock = clock;
        this.offsetMinutes = offsetMinutes;
    }

    @Override
    public Instant departureAt(UUID groupId) {
        return clock.instant().plus(Duration.ofMinutes(offsetMinutes));
    }

    /** Simulates a nearer/farther departure without restarting the service. */
    public void simulateOffsetMinutes(long offsetMinutes) {
        this.offsetMinutes = offsetMinutes;
    }
}
