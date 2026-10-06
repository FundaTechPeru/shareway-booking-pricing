package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "payments")
public class PaymentJpaEntity {
    @Id @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus status;
    @Column(name = "provider_ref", unique = true, length = 100) private String providerRef;
    @Column(name = "authorized_at") private LocalDateTime authorizedAt;
    @Column(name = "captured_at") private LocalDateTime capturedAt;
    @Version @Column(nullable = false) private Long version;
    public UUID getPaymentId() { return paymentId; } public void setPaymentId(UUID v) { paymentId = v; }
    public UUID getBookingId() { return bookingId; } public void setBookingId(UUID v) { bookingId = v; }
    public UUID getPayerId() { return payerId; } public void setPayerId(UUID v) { payerId = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public String getCurrency() { return currency; } public void setCurrency(String v) { currency = v; }
    public PaymentStatus getStatus() { return status; } public void setStatus(PaymentStatus v) { status = v; }
    public String getProviderRef() { return providerRef; } public void setProviderRef(String v) { providerRef = v; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; } public void setAuthorizedAt(LocalDateTime v) { authorizedAt = v; }
    public LocalDateTime getCapturedAt() { return capturedAt; } public void setCapturedAt(LocalDateTime v) { capturedAt = v; }
    public Long getVersion() { return version; } public void setVersion(Long v) { version = v; }
}
