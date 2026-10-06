package com.fundatechperu.shareway.bookingpricing.pricing.domain.service;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    Payment authorize(UUID bookingId, UUID payerId, BigDecimal amount);
}
