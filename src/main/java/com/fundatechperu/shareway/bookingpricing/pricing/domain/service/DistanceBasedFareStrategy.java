package com.fundatechperu.shareway.bookingpricing.pricing.domain.service;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DistanceBasedFareStrategy implements FareCalculationStrategy {
    private final BigDecimal platformFeePercent;
    public DistanceBasedFareStrategy(@Value("${pricing.platform-fee-percent:10}") BigDecimal platformFeePercent) {
        this.platformFeePercent = platformFeePercent;
    }
    @Override public FareCalculation calculate(FareRule rule, BigDecimal distanceKm, int passengers) {
        if (distanceKm.signum() < 0 || passengers <= 0) throw new IllegalArgumentException("Distance and passengers must be positive");
        BigDecimal extra = distanceKm.subtract(rule.baseDistanceKm()).max(BigDecimal.ZERO);
        BigDecimal base = rule.basePrice().add(extra.multiply(rule.pricePerKm())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perPassenger = base.divide(BigDecimal.valueOf(passengers), 2, RoundingMode.HALF_UP);
        BigDecimal fee = base.multiply(platformFeePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new FareCalculation(Money.pen(base), Money.pen(perPassenger), Money.pen(fee), rule.ruleVersion());
    }
}
