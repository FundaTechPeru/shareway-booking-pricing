package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record TripCompleted(UUID eventId, Instant occurredAt, UUID tripId, UUID driverId, UUID groupId,
                            List<UUID> bookingIds) {
    public TripCompleted {
        bookingIds = List.copyOf(bookingIds);
    }
}
