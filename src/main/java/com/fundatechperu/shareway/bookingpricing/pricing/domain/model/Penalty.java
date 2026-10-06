package com.fundatechperu.shareway.bookingpricing.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Penalty(UUID penaltyId, UUID bookingId, BigDecimal amount, String reason, LocalDateTime createdAt) {}
