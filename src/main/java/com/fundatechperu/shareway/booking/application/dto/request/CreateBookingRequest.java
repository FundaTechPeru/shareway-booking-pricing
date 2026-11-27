package com.fundatechperu.shareway.booking.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * POST /api/v1/bookings body. paymentMethodId is optional: when it is omitted Payments uses
 * the passenger's most recently registered active payment method.
 */
public class CreateBookingRequest {

    @NotNull
    private UUID requestId;
    @NotNull
    private UUID groupId;
    @Schema(description = "Optional payment method of the passenger in Payments. When omitted, Payments uses "
            + "the passenger's most recently registered active payment method.", nullable = true)
    private UUID paymentMethodId;

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }
}
