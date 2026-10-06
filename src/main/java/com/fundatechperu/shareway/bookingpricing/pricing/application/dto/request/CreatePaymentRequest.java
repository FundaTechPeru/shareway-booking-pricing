package com.fundatechperu.shareway.bookingpricing.pricing.application.dto.request;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreatePaymentRequest(@NotNull UUID bookingId, @NotNull UUID groupId) {}
