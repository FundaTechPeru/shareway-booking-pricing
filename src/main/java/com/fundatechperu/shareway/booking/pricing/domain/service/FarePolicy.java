package com.fundatechperu.shareway.booking.pricing.domain.service;

import com.fundatechperu.shareway.booking.domain.model.Money;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;

import java.math.BigDecimal;

/**
 * Strategy that prices a shared trip from a fare rule. No Spring or JPA annotations:
 * the bean wiring lives in infrastructure.
 */
public interface FarePolicy {

    /**
     * Quotes the fare a single passenger pays for the group.
     *
     * @param rule              the active fare rule (highest version valid today)
     * @param distanceKm        route distance in kilometres, provided by another service
     * @param groupedPassengers passengers sharing the trip; anything below 1 is treated as 1
     */
    FareQuote quote(FareRule rule, BigDecimal distanceKm, int groupedPassengers);

    /**
     * farePerPassenger: (base_amount + amount_per_km * distance_km) / groupedPassengers
     * platformFee:      farePerPassenger * platform_fee_pct / 100
     * totalPerPassenger: farePerPassenger + platformFee (what the estimate endpoint returns as amount)
     */
    record FareQuote(Money farePerPassenger, Money platformFee, Money totalPerPassenger, int ruleVersion) {
    }
}
