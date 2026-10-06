package com.fundatechperu.shareway.bookingpricing.booking.domain.model;

import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.InvalidBookingTransitionException;

import java.time.LocalDateTime;
import java.util.UUID;

public class Booking {

    private UUID bookingId;
    private final UUID requestId;
    private final UUID groupId;
    private BookingStatus status;
    private String pinHash;
    private String qrTokenHash;
    private Long version;
    private LocalDateTime createdAt;

    private Booking(UUID bookingId, UUID requestId, UUID groupId, BookingStatus status,
                    String pinHash, String qrTokenHash, Long version, LocalDateTime createdAt) {
        this.bookingId = bookingId;
        this.requestId = requestId;
        this.groupId = groupId;
        this.status = status;
        this.pinHash = pinHash;
        this.qrTokenHash = qrTokenHash;
        this.version = version;
        this.createdAt = createdAt;
    }

    public static Booking create(UUID requestId, UUID groupId) {
        return new Booking(UUID.randomUUID(), requestId, groupId, BookingStatus.REQUESTED,
                null, null, null, LocalDateTime.now());
    }

    public static Booking restore(UUID bookingId, UUID requestId, UUID groupId, BookingStatus status,
                                  String pinHash, String qrTokenHash, Long version, LocalDateTime createdAt) {
        return new Booking(bookingId, requestId, groupId, status, pinHash, qrTokenHash, version, createdAt);
    }

    public void confirm() {
        transition(BookingStatus.CONFIRMED, BookingStatus.GROUPED);
    }

    public void markBoarded() {
        transition(BookingStatus.BOARDED, BookingStatus.CONFIRMED);
    }

    public void start() {
        transition(BookingStatus.IN_PROGRESS, BookingStatus.BOARDED);
    }

    public void complete() {
        transition(BookingStatus.COMPLETED, BookingStatus.IN_PROGRESS);
    }

    public void cancel() {
        if (status == BookingStatus.BOARDED || status == BookingStatus.IN_PROGRESS
                || status == BookingStatus.COMPLETED || status == BookingStatus.CANCELLED) {
            throw new InvalidBookingTransitionException(status, BookingStatus.CANCELLED);
        }
        status = BookingStatus.CANCELLED;
    }

    private void transition(BookingStatus target, BookingStatus required) {
        if (status != required) {
            throw new InvalidBookingTransitionException(status, target);
        }
        status = target;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getRequestId() { return requestId; }
    public UUID getGroupId() { return groupId; }
    public BookingStatus getStatus() { return status; }
    public String getPinHash() { return pinHash; }
    public String getQrTokenHash() { return qrTokenHash; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
