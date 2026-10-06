package com.fundatechperu.shareway.bookingpricing.shared.events;
import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate; import java.util.UUID;
public record PaymentAuthorizationRequested(UUID eventId, Instant occurredAt, UUID bookingId, UUID groupId,
                                            UUID payerId, String zone, LocalDate date, BigDecimal distanceKm,
                                            int passengers) {}
