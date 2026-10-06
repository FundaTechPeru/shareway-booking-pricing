package com.fundatechperu.shareway.bookingpricing.booking.application.dto.response;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class BookingResponse {

    private final UUID bookingId;
    private final UUID requestId;
    private final UUID groupId;
    private final BookingStatus status;
    private final Long version;
    private final LocalDateTime createdAt;

    private BookingResponse(Booking booking) {
        this.bookingId = booking.getBookingId();
        this.requestId = booking.getRequestId();
        this.groupId = booking.getGroupId();
        this.status = booking.getStatus();
        this.version = booking.getVersion();
        this.createdAt = booking.getCreatedAt();
    }

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking);
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
