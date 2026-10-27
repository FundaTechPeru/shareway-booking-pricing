package com.fundatechperu.shareway.booking.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Money value object: amount plus ISO currency (defaults to PEN).
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() < 0) throw new IllegalArgumentException("Money amount cannot be negative");
        currency = (currency == null || currency.isBlank()) ? "PEN" : currency;
    }

    public static Money pen(BigDecimal amount) {
        return new Money(amount, "PEN");
    }
}
