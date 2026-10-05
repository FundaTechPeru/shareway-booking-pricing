package com.fundatechperu.shareway.bookingpricing.booking.application.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Booking {

    private UUID bookingId;
    private UUID requestId;
    private UUID groupId;
    private BookingStatus status;
    private String pinHash;
    private String qrTokenHash;
    private Long version;
    private LocalDateTime createdAt;

    public Booking(UUID requestId, UUID groupId) {
        this.bookingId = UUID.randomUUID();
        this.requestId = requestId;
        this.groupId = groupId;
        this.status = BookingStatus.REQUESTED;
        this.version = 0L;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public String getQrTokenHash() {
        return qrTokenHash;
    }

    public void setQrTokenHash(String qrTokenHash) {
        this.qrTokenHash = qrTokenHash;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
