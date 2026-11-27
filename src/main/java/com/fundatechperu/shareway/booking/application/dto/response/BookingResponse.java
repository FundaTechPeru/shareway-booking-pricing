package com.fundatechperu.shareway.booking.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Booking body of POST /api/v1/bookings, GET /api/v1/bookings and GET /api/v1/bookings/{id},
 * with the status in uppercase (REQUESTED, ...). boardingPin and qrToken are present only
 * for the owner of a booking that is CONFIRMED or further: the passenger shows one of them
 * to the driver when boarding. An admin reading someone else's booking never gets them.
 */
@Schema(description = "A seat reservation")
public record BookingResponse(
        UUID bookingId,
        UUID groupId,
        UUID requestId,
        BookingStatus status,
        @Schema(description = "Estimated fare authorized in Payments when the booking was created", example = "21.34")
        BigDecimal estimatedAmount,
        @Schema(example = "PEN")
        String currency,
        @Schema(description = "6-digit boarding PIN; only for the owner once the booking is CONFIRMED",
                example = "004271", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String boardingPin,
        @Schema(description = "QR token to show when boarding; only for the owner once the booking is CONFIRMED",
                example = "9f86d081884c7d659a2feaa0c55ad015", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String qrToken) {

    public BookingResponse(UUID bookingId, BookingStatus status) {
        this(bookingId, null, null, status, null, null, null, null);
    }

    /** View of the owner: the boarding codes are included once the booking is confirmed. */
    public static BookingResponse forOwner(Booking booking) {
        boolean codes = booking.hasBoardingCodesVisible();
        return new BookingResponse(booking.getBookingId(), booking.getGroupId(), booking.getRequestId(),
                booking.getStatus(), booking.getEstimatedAmount(), booking.getCurrency(),
                codes ? booking.getBoardingPin() : null, codes ? booking.getQrToken() : null);
    }

    /** View of anybody else (an admin): never carries the boarding codes. */
    public static BookingResponse withoutCodes(Booking booking) {
        return new BookingResponse(booking.getBookingId(), booking.getGroupId(), booking.getRequestId(),
                booking.getStatus(), booking.getEstimatedAmount(), booking.getCurrency(), null, null);
    }

    /** The view each requester is entitled to. */
    public static BookingResponse viewedBy(Booking booking, UUID requesterId) {
        return booking.getPassengerId().equals(requesterId) ? forOwner(booking) : withoutCodes(booking);
    }
}
