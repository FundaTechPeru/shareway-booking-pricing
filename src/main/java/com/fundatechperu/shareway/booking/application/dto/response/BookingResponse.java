package com.fundatechperu.shareway.booking.application.dto.response;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;

import java.util.UUID;

/**
 * POST /api/v1/bookings and GET /api/v1/bookings/{id} body: {bookingId, status} with the status
 * in uppercase (REQUESTED, ...) as required by the API contract.
 */
public record BookingResponse(UUID bookingId, BookingStatus status) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getBookingId(), booking.getStatus());
    }
}
