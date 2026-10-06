package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.UUID;
public record BookingCancelled(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId,
                               UUID payerId, boolean penaltyApplicable) {}
