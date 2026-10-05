package com.fundatechperu.shareway.bookingpricing.booking.domain.dto;

import java.util.UUID;

public class BookingRequestDto {

    private UUID requestId;
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
