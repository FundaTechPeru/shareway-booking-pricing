package com.fundatechperu.shareway.bookingpricing.pricing.domain.service;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import java.math.BigDecimal;

public interface FareCalculationStrategy {
    FareCalculation calculate(FareRule rule, BigDecimal distanceKm, int passengers);
    record FareCalculation(Money baseAmount, Money amountPerPassenger, Money platformFee, int ruleVersion) {}
}
