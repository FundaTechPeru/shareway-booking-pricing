package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void keepsAmountAndCurrency() {
        Money money = new Money(new BigDecimal("12.34"), "USD");
        assertEquals(new BigDecimal("12.34"), money.amount());
        assertEquals("USD", money.currency());
    }

    @Test
    void defaultsToPenWhenCurrencyIsMissing() {
        assertEquals("PEN", new Money(BigDecimal.ONE, null).currency());
        assertEquals("PEN", new Money(BigDecimal.ONE, "  ").currency());
        assertEquals("PEN", Money.pen(BigDecimal.ONE).currency());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-0.01"), "PEN"));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null, "PEN"));
    }
}
