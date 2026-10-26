package com.fundatechperu.shareway.booking.shared.events;
import java.time.Instant; import java.util.UUID;
public record GroupCompleted(UUID eventId, Instant occurredAt, UUID groupId) {}
