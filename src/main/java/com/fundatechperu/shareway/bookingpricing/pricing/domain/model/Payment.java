package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Payment {
    private final UUID paymentId;
    private final UUID bookingId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final String providerRef;
    private final LocalDateTime authorizedAt;
    private LocalDateTime capturedAt;
    public Payment(UUID paymentId, UUID bookingId, BigDecimal amount, String currency, PaymentStatus status,
                   String providerRef, LocalDateTime authorizedAt) {
        this.paymentId = paymentId; this.bookingId = bookingId; this.amount = amount;
        this.currency = currency; this.status = status; this.providerRef = providerRef; this.authorizedAt = authorizedAt;
    }
    public void capture() { if (status != PaymentStatus.AUTHORIZED) throw invalid(); status = PaymentStatus.CAPTURED; capturedAt = LocalDateTime.now(); }
    public void reject() { if (status != PaymentStatus.AUTHORIZED) throw invalid(); status = PaymentStatus.REJECTED; }
    public void refund() { if (status != PaymentStatus.CAPTURED) throw invalid(); status = PaymentStatus.REFUNDED; }
    private IllegalStateException invalid() { return new IllegalStateException("Invalid payment transition from " + status); }
    public UUID getPaymentId() { return paymentId; } public UUID getBookingId() { return bookingId; }
    public BigDecimal getAmount() { return amount; } public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; } public String getProviderRef() { return providerRef; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; } public LocalDateTime getCapturedAt() { return capturedAt; }
}
