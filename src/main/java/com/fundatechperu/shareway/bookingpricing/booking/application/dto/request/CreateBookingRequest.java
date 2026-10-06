package com.fundatechperu.shareway.bookingpricing.booking.application.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public class CreateBookingRequest {

    @NotNull
    private UUID requestId;
    @NotNull
    private UUID groupId;

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
}
