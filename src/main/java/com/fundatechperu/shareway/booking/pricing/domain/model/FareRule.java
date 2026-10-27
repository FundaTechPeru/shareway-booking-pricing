package com.fundatechperu.shareway.booking.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FareRule(Long ruleId, int version, BigDecimal baseAmount, BigDecimal amountPerKm,
                       BigDecimal platformFeePct, LocalDate validFrom, LocalDate validTo) {
}
