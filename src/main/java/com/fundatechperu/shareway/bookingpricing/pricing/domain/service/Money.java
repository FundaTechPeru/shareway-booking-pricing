package com.fundatechperu.shareway.bookingpricing.pricing.domain.service;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount);
        if (amount.signum() < 0) throw new IllegalArgumentException("Money amount cannot be negative");
        currency = currency == null ? "PEN" : currency;
    }
    public static Money pen(BigDecimal amount) { return new Money(amount, "PEN"); }
}
