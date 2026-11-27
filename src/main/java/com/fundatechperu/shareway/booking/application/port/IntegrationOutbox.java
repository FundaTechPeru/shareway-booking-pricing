package com.fundatechperu.shareway.booking.application.port;

import com.fundatechperu.shareway.booking.domain.model.CancellationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port toward Payments and Trip Execution. Every method stores ONE message in the
 * outbox inside the caller's transaction (it fails without one), so the message exists if
 * and only if the change that produced it was committed. The outbox relay sends the messages
 * later, in the order they were stored.
 */
public interface IntegrationOutbox {

    /** POST {PAYMENTS_URL}/api/v1/internal/payments/authorizations. paymentMethodId may be null. */
    void requestPaymentAuthorization(UUID bookingId, UUID passengerId, UUID paymentMethodId,
                                     BigDecimal amount, String currency);

    /** POST {PAYMENTS_URL}/api/v1/internal/events/booking-cancelled. */
    void bookingCancelled(UUID bookingId, CancellationReason reason, Instant cancelledAt,
                          Instant scheduledDepartureAt);

    /** POST {TRIP_EXECUTION_URL}/api/internal/grupos-confirmados. passengers is never empty. */
    void groupConfirmed(UUID groupId, UUID driverId, List<ConfirmedPassenger> passengers);

    /** POST {PAYMENTS_URL}/api/v1/internal/events/trip-completed. bookings is never empty. */
    void tripCompleted(UUID tripId, UUID driverId, Instant completedAt, List<BookingCharge> bookings);

    /**
     * One passenger of a confirmed group. codeHash and pinHash are the lowercase hexadecimal
     * SHA-256 of the QR token and of the PIN.
     */
    record ConfirmedPassenger(UUID bookingId, UUID passengerId, String codeHash, String pinHash) {
    }

    /** Amount to capture for one booking of a completed trip. */
    record BookingCharge(UUID bookingId, BigDecimal finalAmount) {
    }
}
