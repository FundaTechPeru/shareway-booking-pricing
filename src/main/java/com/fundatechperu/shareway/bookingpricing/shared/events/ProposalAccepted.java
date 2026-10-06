package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.UUID;
public record ProposalAccepted(UUID eventId, Instant occurredAt, UUID groupId, UUID driverId) {}
