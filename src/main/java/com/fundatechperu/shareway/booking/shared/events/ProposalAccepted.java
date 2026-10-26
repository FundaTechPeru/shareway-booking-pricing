package com.fundatechperu.shareway.booking.shared.events;
import java.time.Instant; import java.util.UUID;
public record ProposalAccepted(UUID eventId, Instant occurredAt, UUID groupId, UUID driverId) {}
