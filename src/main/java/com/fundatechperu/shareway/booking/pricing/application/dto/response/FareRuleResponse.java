package com.fundatechperu.shareway.booking.pricing.application.dto.response;

import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FareRuleResponse(Long ruleId, int version, BigDecimal baseAmount, BigDecimal amountPerKm,
                               BigDecimal platformFeePct, LocalDate validFrom, LocalDate validTo) {
    public static FareRuleResponse from(FareRule rule) {
        return new FareRuleResponse(rule.ruleId(), rule.version(), rule.baseAmount(), rule.amountPerKm(),
                rule.platformFeePct(), rule.validFrom(), rule.validTo());
    }
}
