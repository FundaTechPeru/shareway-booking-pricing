package com.fundatechperu.shareway.booking.pricing.application.dto.response;

import java.math.BigDecimal;

/**
 * 200 body of GET /fares/estimate: nothing is persisted, only the quote is returned.
 */
public record FareEstimateResponse(BigDecimal amount, String currency, int ruleVersion) {
}
