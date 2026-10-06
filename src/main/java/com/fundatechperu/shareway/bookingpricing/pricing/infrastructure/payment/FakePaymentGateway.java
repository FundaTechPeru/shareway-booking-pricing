package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.payment;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.PaymentGateway;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {
    private final String mode;
    public FakePaymentGateway(@Value("${pricing.payment.fake.mode:OK}") String mode) { this.mode = mode; }
    @Override public Payment authorize(UUID bookingId, UUID payerId, BigDecimal amount) {
        if ("FAIL".equalsIgnoreCase(mode)) throw new IllegalStateException("Payment provider rejected authorization");
        if ("SLOW".equalsIgnoreCase(mode)) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return new Payment(UUID.randomUUID(), bookingId, payerId, amount, "PEN", PaymentStatus.AUTHORIZED,
                "fake-" + UUID.randomUUID(), LocalDateTime.now());
    }
}
