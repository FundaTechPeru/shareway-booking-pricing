package com.fundatechperu.shareway.booking.pricing.domain.service;

import com.fundatechperu.shareway.booking.domain.model.Money;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Distance-based {@link FarePolicy}:
 * total trip cost = base_amount + amount_per_km * distance_km, split among the grouped
 * passengers (minimum 1), plus the platform fee (platform_fee_pct) on the passenger share.
 * All money is rounded HALF_UP to 2 decimals.
 */
public class DistanceBasedFarePolicy implements FarePolicy {

    @Override
    public FareQuote quote(FareRule rule, BigDecimal distanceKm, int groupedPassengers) {
        if (rule == null) throw new IllegalArgumentException("fare rule is required");
        if (distanceKm == null || distanceKm.signum() < 0)
            throw new IllegalArgumentException("distanceKm must not be negative");

        BigDecimal tripCost = rule.baseAmount()
                .add(rule.amountPerKm().multiply(distanceKm))
                .setScale(2, RoundingMode.HALF_UP);

        int passengers = Math.max(groupedPassengers, 1);
        BigDecimal farePerPassenger = tripCost.divide(BigDecimal.valueOf(passengers), 2, RoundingMode.HALF_UP);
        BigDecimal platformFee = farePerPassenger.multiply(rule.platformFeePct())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalPerPassenger = farePerPassenger.add(platformFee);

        return new FareQuote(Money.pen(farePerPassenger), Money.pen(platformFee),
                Money.pen(totalPerPassenger), rule.version());
    }
}
