package com.fundatechperu.shareway.bookingpricing.pricing.application.dto.response;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.UUID;
public record PaymentResponse(UUID paymentId, UUID bookingId, BigDecimal amount, String currency,
                              PaymentStatus status, String providerRef, LocalDateTime authorizedAt,
                              LocalDateTime capturedAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getPaymentId(), p.getBookingId(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getProviderRef(), p.getAuthorizedAt(), p.getCapturedAt());
    }
}
