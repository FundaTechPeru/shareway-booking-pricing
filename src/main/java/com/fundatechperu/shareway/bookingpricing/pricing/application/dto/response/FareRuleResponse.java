package com.fundatechperu.shareway.bookingpricing.pricing.application.dto.response;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.FareRuleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FareRuleResponse(Long fareRuleId, String zone, BigDecimal baseDistanceKm, BigDecimal basePrice,
                               BigDecimal pricePerKm, LocalDate validFrom, LocalDate validUntil,
                               FareRuleStatus status, int ruleVersion) {
    public static FareRuleResponse from(FareRule rule) {
        return new FareRuleResponse(rule.fareRuleId(), rule.zone(), rule.baseDistanceKm(), rule.basePrice(),
                rule.pricePerKm(), rule.validFrom(), rule.validUntil(), rule.status(), rule.ruleVersion());
    }
}
