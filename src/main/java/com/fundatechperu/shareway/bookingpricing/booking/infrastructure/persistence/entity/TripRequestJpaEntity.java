package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "trip_requests")
public class TripRequestJpaEntity {
    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
}
