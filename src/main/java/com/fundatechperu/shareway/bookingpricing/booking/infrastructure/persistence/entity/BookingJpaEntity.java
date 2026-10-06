package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class BookingJpaEntity {

    @Id
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "request_id", unique = true, nullable = false)
    private UUID requestId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "qr_token_hash")
    private String qrTokenHash;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "payment_authorized_at")
    private LocalDateTime paymentAuthorizedAt;

    public BookingJpaEntity() {
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
    public LocalDateTime getPaymentAuthorizedAt() { return paymentAuthorizedAt; }
    public void setPaymentAuthorizedAt(LocalDateTime paymentAuthorizedAt) { this.paymentAuthorizedAt = paymentAuthorizedAt; }
}
