package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.payment;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.PaymentStatus;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.PaymentGateway;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {
    @Override public Payment authorize(UUID bookingId, BigDecimal amount) {
        return new Payment(UUID.randomUUID(), bookingId, amount, "PEN", PaymentStatus.AUTHORIZED,
                "fake-" + UUID.randomUUID(), LocalDateTime.now());
    }
}
