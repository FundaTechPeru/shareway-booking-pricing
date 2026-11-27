package com.fundatechperu.shareway.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class BookingJpaEntity {

    @Id
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "passenger_id", nullable = false)
    private UUID passengerId;

    /** Stored in lowercase: requested | grouped | ... (CHECK in V1). Enum values are uppercase in the API. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "qr_token_hash")
    private String qrTokenHash;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "boarding_pin", length = 6)
    private String boardingPin;

    @Column(name = "qr_token", length = 64)
    private String qrToken;

    @Column(name = "estimated_amount", precision = 10, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payment_authorized_at")
    private LocalDateTime paymentAuthorizedAt;

    @Column(name = "cancellation_reason", length = 30)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BookingJpaEntity() {
    }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public UUID getPassengerId() { return passengerId; }
    public void setPassengerId(UUID passengerId) { this.passengerId = passengerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public String getQrTokenHash() { return qrTokenHash; }
    public void setQrTokenHash(String qrTokenHash) { this.qrTokenHash = qrTokenHash; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getBoardingPin() { return boardingPin; }
    public void setBoardingPin(String boardingPin) { this.boardingPin = boardingPin; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public LocalDateTime getPaymentAuthorizedAt() { return paymentAuthorizedAt; }
    public void setPaymentAuthorizedAt(LocalDateTime paymentAuthorizedAt) { this.paymentAuthorizedAt = paymentAuthorizedAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
