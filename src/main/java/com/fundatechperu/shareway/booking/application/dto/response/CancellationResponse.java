package com.fundatechperu.shareway.booking.application.dto.response;

import com.fundatechperu.shareway.booking.domain.model.BookingStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * POST /bookings/{id}/cancel body: {bookingId, status, penaltyAmount, currency} with the
 * status in uppercase. penaltyAmount is null when the cancellation was not late (no
 * penalty was created) or when the booking was already cancelled.
 */
public record CancellationResponse(UUID bookingId, BookingStatus status, BigDecimal penaltyAmount, String currency) {
}
