package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.time.Instant; import java.util.UUID;
public record PaymentAuthorized(UUID eventId, Instant occurredAt, UUID bookingId, UUID paymentId) {}
