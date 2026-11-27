package com.fundatechperu.shareway.booking.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One call to another service, stored in the transaction of the change that produced it
 * (outbox_messages, V3). It is pending until it is processed (delivered, or answered in a way
 * that settles it) or failed (rejected for good). sequence_number is assigned by the database
 * and gives the sending order.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private OutboxMessageType type;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxMessage() {
    }

    public OutboxMessage(UUID id, OutboxMessageType type, UUID aggregateId, String payload, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public void markProcessed(Instant now, String note) {
        attempts++;
        processedAt = now;
        lastError = truncate(note);
    }

    public void scheduleRetry(Instant now, Duration delay, String error) {
        attempts++;
        nextAttemptAt = now.plus(delay);
        lastError = truncate(error);
    }

    public void markFailed(Instant now, String error) {
        attempts++;
        failedAt = now;
        lastError = truncate(error);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() { return id; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public OutboxMessageType getType() { return type; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getLastError() { return lastError; }
}
