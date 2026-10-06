package com.fundatechperu.shareway.bookingpricing.pricing;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.*;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.DistanceBasedFareStrategy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceBasedFareStrategyTest {
    private final FareRule rule = new FareRule(1L, "Central", new BigDecimal("5"),
            new BigDecimal("10"), new BigDecimal("2.5"), LocalDate.now(), LocalDate.now(),
            FareRuleStatus.ACTIVE, 3);
    @Test void usesBasePriceInsideBaseDistance() {
        var result = new DistanceBasedFareStrategy(new BigDecimal("10")).calculate(rule, new BigDecimal("4"), 1);
        assertEquals(new BigDecimal("10.00"), result.baseAmount().amount());
    }
    @Test void chargesDistanceAndRoundsPerPassenger() {
        var result = new DistanceBasedFareStrategy(new BigDecimal("10")).calculate(rule, new BigDecimal("6"), 3);
        assertEquals(new BigDecimal("12.50"), result.baseAmount().amount());
        assertEquals(new BigDecimal("4.17"), result.amountPerPassenger().amount());
        assertEquals(3, result.ruleVersion());
    }
}
