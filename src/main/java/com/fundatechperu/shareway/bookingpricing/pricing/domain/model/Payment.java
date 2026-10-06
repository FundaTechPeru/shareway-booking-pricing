package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.InvalidPaymentTransitionException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Payment {
    private final UUID paymentId;
    private final UUID bookingId;
    private final UUID payerId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final String providerRef;
    private final LocalDateTime authorizedAt;
    private LocalDateTime capturedAt;
    private Long version;
    public Payment(UUID paymentId, UUID bookingId, UUID payerId, BigDecimal amount, String currency,
                   PaymentStatus status, String providerRef, LocalDateTime authorizedAt) {
        this(paymentId, bookingId, payerId, amount, currency, status, providerRef, authorizedAt, null, null);
    }
    private Payment(UUID paymentId, UUID bookingId, UUID payerId, BigDecimal amount, String currency,
                    PaymentStatus status, String providerRef, LocalDateTime authorizedAt,
                    LocalDateTime capturedAt, Long version) {
        this.paymentId = paymentId; this.bookingId = bookingId; this.payerId = payerId; this.amount = amount;
        this.currency = currency; this.status = status; this.providerRef = providerRef;
        this.authorizedAt = authorizedAt; this.capturedAt = capturedAt; this.version = version;
    }
    public static Payment restore(UUID paymentId, UUID bookingId, UUID payerId, BigDecimal amount, String currency,
                                  PaymentStatus status, String providerRef, LocalDateTime authorizedAt,
                                  LocalDateTime capturedAt, Long version) {
        return new Payment(paymentId, bookingId, payerId, amount, currency, status, providerRef,
                authorizedAt, capturedAt, version);
    }
    public void authorize() { if (status != PaymentStatus.AUTHORIZED) throw invalid(); }
    public void capture() { if (status != PaymentStatus.AUTHORIZED) throw invalid(); status = PaymentStatus.CAPTURED; capturedAt = LocalDateTime.now(); }
    public void reject(String reason) { if (status != PaymentStatus.AUTHORIZED) throw invalid(); status = PaymentStatus.REJECTED; }
    public void refund() { if (status != PaymentStatus.CAPTURED) throw invalid(); status = PaymentStatus.REFUNDED; }
    public void voidAuthorization() { if (status != PaymentStatus.AUTHORIZED) throw invalid(); status = PaymentStatus.VOIDED; }
    private InvalidPaymentTransitionException invalid() { return new InvalidPaymentTransitionException(status); }
    public UUID getPaymentId() { return paymentId; } public UUID getBookingId() { return bookingId; }
    public UUID getPayerId() { return payerId; } public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; } public PaymentStatus getStatus() { return status; }
    public String getProviderRef() { return providerRef; } public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public LocalDateTime getCapturedAt() { return capturedAt; } public Long getVersion() { return version; }
}
