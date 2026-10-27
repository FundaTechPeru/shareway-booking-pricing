package com.fundatechperu.shareway.booking.domain.model;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingTransitionException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Booking aggregate with its state machine. Valid transitions:
 * requested -> grouped -> confirmed -> boarded -> in_progress -> completed,
 * plus requested|grouped|confirmed -> cancelled. Any other transition throws
 * {@link InvalidBookingTransitionException}.
 */
public class Booking {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.REQUESTED, Set.of(BookingStatus.GROUPED, BookingStatus.CANCELLED),
            BookingStatus.GROUPED, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.BOARDED, BookingStatus.CANCELLED),
            BookingStatus.BOARDED, Set.of(BookingStatus.IN_PROGRESS),
            BookingStatus.IN_PROGRESS, Set.of(BookingStatus.COMPLETED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of()
    );

    private UUID bookingId;
    private final UUID requestId;
    private final UUID groupId;
    private final UUID passengerId;
    private final String idempotencyKey;
    private BookingStatus status;
    private String pinHash;
    private String qrTokenHash;
    private Long version;
    private LocalDateTime createdAt;

    private Booking(UUID bookingId, UUID requestId, UUID groupId, UUID passengerId, String idempotencyKey,
                    BookingStatus status, String pinHash, String qrTokenHash, Long version, LocalDateTime createdAt) {
        this.bookingId = bookingId;
        this.requestId = requestId;
        this.groupId = groupId;
        this.passengerId = passengerId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.pinHash = pinHash;
        this.qrTokenHash = qrTokenHash;
        this.version = version;
        this.createdAt = createdAt;
    }

    public static Booking create(UUID requestId, UUID groupId, UUID passengerId, String idempotencyKey) {
        return new Booking(UUID.randomUUID(), requestId, groupId, passengerId, idempotencyKey,
                BookingStatus.REQUESTED, null, null, null, LocalDateTime.now());
    }

    public static Booking restore(UUID bookingId, UUID requestId, UUID groupId, UUID passengerId,
                                  String idempotencyKey, BookingStatus status, String pinHash, String qrTokenHash,
                                  Long version, LocalDateTime createdAt) {
        return new Booking(bookingId, requestId, groupId, passengerId, idempotencyKey, status, pinHash,
                qrTokenHash, version, createdAt);
    }

    /**
     * Applies the state machine: only the valid transitions are accepted.
     */
    public void transitionTo(BookingStatus target) {
        if (target == null) throw new InvalidBookingTransitionException(status, null);
        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status, Set.of());
        if (!allowed.contains(target)) throw new InvalidBookingTransitionException(status, target);
        status = target;
    }

    public void group() { transitionTo(BookingStatus.GROUPED); }

    public void confirm() { transitionTo(BookingStatus.CONFIRMED); }

    public void markBoarded() { transitionTo(BookingStatus.BOARDED); }

    public void start() { transitionTo(BookingStatus.IN_PROGRESS); }

    public void complete() { transitionTo(BookingStatus.COMPLETED); }

    public void cancel() { transitionTo(BookingStatus.CANCELLED); }

    /**
     * Stores the boarding codes of a confirmed booking. Only the HASHES are kept
     * (BCrypt for the PIN, SHA-256 for the QR token): the clear values travel once in
     * the BookingConfirmed event and are never exposed by the API.
     */
    public void assignBoardingCodes(String pinHash, String qrTokenHash) {
        this.pinHash = pinHash;
        this.qrTokenHash = qrTokenHash;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getRequestId() { return requestId; }
    public UUID getGroupId() { return groupId; }
    public UUID getPassengerId() { return passengerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BookingStatus getStatus() { return status; }
    public String getPinHash() { return pinHash; }
    public String getQrTokenHash() { return qrTokenHash; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
