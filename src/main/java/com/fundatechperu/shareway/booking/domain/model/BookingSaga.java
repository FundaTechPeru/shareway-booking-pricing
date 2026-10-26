package com.fundatechperu.shareway.booking.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Process manager of a booking reservation: tracks the current step of the
 * reservation/payment flow and its final outcome (completed or compensated).
 */
public class BookingSaga {

    public enum Status { RUNNING, COMPLETED, COMPENSATED }

    private final UUID sagaId;
    private final UUID bookingId;
    private String currentStep;
    private Status status;
    private LocalDateTime deadlineAt;
    private LocalDateTime updatedAt;

    private BookingSaga(UUID sagaId, UUID bookingId, String currentStep, Status status,
                        LocalDateTime deadlineAt, LocalDateTime updatedAt) {
        this.sagaId = sagaId;
        this.bookingId = bookingId;
        this.currentStep = currentStep;
        this.status = status;
        this.deadlineAt = deadlineAt;
        this.updatedAt = updatedAt;
    }

    public static BookingSaga start(UUID bookingId, String currentStep, LocalDateTime deadlineAt) {
        return new BookingSaga(UUID.randomUUID(), bookingId, currentStep, Status.RUNNING,
                deadlineAt, LocalDateTime.now());
    }

    public static BookingSaga restore(UUID sagaId, UUID bookingId, String currentStep, Status status,
                                      LocalDateTime deadlineAt, LocalDateTime updatedAt) {
        return new BookingSaga(sagaId, bookingId, currentStep, status, deadlineAt, updatedAt);
    }

    public void advance(String step) {
        if (status != Status.RUNNING) throw new IllegalStateException("Only a running saga can advance");
        currentStep = step;
        updatedAt = LocalDateTime.now();
    }

    /** Moves the deadline of the current step (each step has its own timeout). */
    public void reschedule(LocalDateTime newDeadlineAt) {
        this.deadlineAt = newDeadlineAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        status = Status.COMPLETED;
        updatedAt = LocalDateTime.now();
    }

    public void compensate() {
        status = Status.COMPENSATED;
        updatedAt = LocalDateTime.now();
    }

    public UUID getSagaId() { return sagaId; }
    public UUID getBookingId() { return bookingId; }
    public String getCurrentStep() { return currentStep; }
    public Status getStatus() { return status; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
