package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "penalties")
public class PenaltyJpaEntity {
    @Id @Column(name = "penalty_id", nullable = false) private UUID penaltyId;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 50) private String reason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    public UUID getPenaltyId() { return penaltyId; } public void setPenaltyId(UUID v) { penaltyId = v; }
    public UUID getBookingId() { return bookingId; } public void setBookingId(UUID v) { bookingId = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public String getReason() { return reason; } public void setReason(String v) { reason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
