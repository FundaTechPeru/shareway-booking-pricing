package com.fundatechperu.shareway.booking.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumed event (Trip Execution): the trip of a group finished. The boarded bookings move to
 * completed and their final fare is priced with the REAL distance (distanceKm may be null:
 * GroupInfoProvider supplies it then). Bookings still confirmed that are not in
 * boardedBookingIds never boarded and are cancelled as NO_SHOW.
 */
public record TripCompleted(UUID eventId, Instant occurredAt, UUID groupId, UUID tripId, UUID driverId,
                            Instant completedAt, BigDecimal distanceKm, List<UUID> boardedBookingIds) {

    public TripCompleted {
        boardedBookingIds = boardedBookingIds == null ? List.of() : List.copyOf(boardedBookingIds);
    }

    public TripCompleted(UUID eventId, Instant occurredAt, UUID groupId, BigDecimal distanceKm) {
        this(eventId, occurredAt, groupId, null, null, occurredAt, distanceKm, List.of());
    }
}
