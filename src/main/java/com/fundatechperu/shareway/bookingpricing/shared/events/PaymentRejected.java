package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.UUID;
public record PaymentRejected(UUID eventId, Instant occurredAt, UUID bookingId, String reason) {}
