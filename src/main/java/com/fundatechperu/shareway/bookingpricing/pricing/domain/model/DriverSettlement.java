package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record DriverSettlement(UUID settlementId, UUID tripId, UUID driverId, BigDecimal grossAmount,
                               BigDecimal commission, BigDecimal netAmount, String status) {}
