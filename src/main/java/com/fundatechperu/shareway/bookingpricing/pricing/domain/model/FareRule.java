package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FareRule(Long fareRuleId, String zone, BigDecimal baseDistanceKm, BigDecimal basePrice,
                       BigDecimal pricePerKm, LocalDate validFrom, LocalDate validUntil,
                       FareRuleStatus status, int ruleVersion) {
}
