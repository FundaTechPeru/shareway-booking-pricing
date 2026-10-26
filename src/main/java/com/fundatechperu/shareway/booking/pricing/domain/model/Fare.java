package com.fundatechperu.shareway.booking.pricing.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Fare(UUID fareId, UUID bookingId, Long ruleId, String fareType, BigDecimal distanceKm,
                   int groupedPassengers, BigDecimal amount, String currency) {

    public static final String ESTIMATED = "estimated";
    public static final String FINAL = "final";
}
