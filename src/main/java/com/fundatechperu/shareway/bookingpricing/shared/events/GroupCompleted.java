package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.UUID;
public record GroupCompleted(UUID eventId, Instant occurredAt, UUID groupId) {}
