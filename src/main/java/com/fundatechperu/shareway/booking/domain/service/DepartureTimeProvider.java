package com.fundatechperu.shareway.booking.domain.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port to Driver Operations & Routing: departure time of a trip group, needed by
 * the CancellationPolicy to decide whether a cancellation is late. The departure is not
 * owned by this schema, so it is resolved through this port (Sprint 2).
 */
public interface DepartureTimeProvider {

    /**
     * Scheduled departure of a trip group.
     */
    Instant departureAt(UUID groupId);
}
