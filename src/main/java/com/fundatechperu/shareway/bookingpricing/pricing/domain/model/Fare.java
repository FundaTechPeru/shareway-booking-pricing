package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Fare(UUID fareId, UUID groupId, Long fareRuleId, int ruleVersion,
                   BigDecimal baseAmount, BigDecimal amountPerPassenger,
                   BigDecimal platformFee, LocalDateTime createdAt) {
}
