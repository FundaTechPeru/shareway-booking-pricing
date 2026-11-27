package com.fundatechperu.shareway.booking.domain.model;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingTransitionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
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

    /** Statuses from which the booking can still be cancelled. */
    public static final Set<BookingStatus> CANCELLABLE_STATUSES =
            EnumSet.of(BookingStatus.REQUESTED, BookingStatus.GROUPED, BookingStatus.CONFIRMED);

    /** Statuses in which the passenger holds the boarding codes (confirmed or further). */
    private static final Set<BookingStatus> WITH_BOARDING_CODES = EnumSet.of(BookingStatus.CONFIRMED,
            BookingStatus.BOARDED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

    private UUID bookingId;
    private final UUID requestId;
    private final UUID groupId;
    private final UUID passengerId;
    private final String idempotencyKey;
    private BookingStatus status;
    private String pinHash;
    private String qrTokenHash;
    private String boardingPin;
    private String qrToken;
    private BigDecimal estimatedAmount;
    private String currency;
    private UUID paymentId;
    private LocalDateTime paymentAuthorizedAt;
    private CancellationReason cancellationReason;
    private LocalDateTime cancelledAt;
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

    /** Restores the integration data persisted next to the core fields (V3 columns). */
    public Booking withIntegrationData(String boardingPin, String qrToken, BigDecimal estimatedAmount,
                                       String currency, UUID paymentId, LocalDateTime paymentAuthorizedAt,
                                       CancellationReason cancellationReason, LocalDateTime cancelledAt) {
        this.boardingPin = boardingPin;
        this.qrToken = qrToken;
        this.estimatedAmount = estimatedAmount;
        this.currency = currency;
        this.paymentId = paymentId;
        this.paymentAuthorizedAt = paymentAuthorizedAt;
        this.cancellationReason = cancellationReason;
        this.cancelledAt = cancelledAt;
        return this;
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

    /** Cancels the booking recording why and when. */
    public void cancel(CancellationReason reason, LocalDateTime at) {
        cancel();
        this.cancellationReason = reason;
        this.cancelledAt = at;
    }

    public boolean isCancellable() { return CANCELLABLE_STATUSES.contains(status); }

    /** Estimated fare sent to Payments for the authorization. */
    public void assignEstimate(BigDecimal amount, String currency) {
        this.estimatedAmount = amount;
        this.currency = currency;
    }

    /** Payments authorized the estimated amount. Idempotent: the first authorization wins. */
    public void recordPaymentAuthorization(UUID paymentId, LocalDateTime at) {
        if (paymentAuthorizedAt != null) return;
        this.paymentId = paymentId;
        this.paymentAuthorizedAt = at;
    }

    public boolean isPaymentAuthorized() { return paymentAuthorizedAt != null; }

    /** The boarding codes are shown to the passenger once the booking is confirmed. */
    public boolean hasBoardingCodesVisible() { return WITH_BOARDING_CODES.contains(status); }

    /**
     * Stores the hashes of the boarding codes of a confirmed booking (BCrypt for the PIN,
     * SHA-256 for the QR token).
     */
    public void assignBoardingCodes(String pinHash, String qrTokenHash) {
        this.pinHash = pinHash;
        this.qrTokenHash = qrTokenHash;
    }

    /**
     * Stores the boarding codes of a confirmed booking: the clear PIN and QR token (returned
     * only to the owner, who shows them when boarding) plus their hashes.
     */
    public void assignBoardingCodes(String boardingPin, String qrToken, String pinHash, String qrTokenHash) {
        assignBoardingCodes(pinHash, qrTokenHash);
        this.boardingPin = boardingPin;
        this.qrToken = qrToken;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getRequestId() { return requestId; }
    public UUID getGroupId() { return groupId; }
    public UUID getPassengerId() { return passengerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BookingStatus getStatus() { return status; }
    public String getPinHash() { return pinHash; }
    public String getQrTokenHash() { return qrTokenHash; }
    public String getBoardingPin() { return boardingPin; }
    public String getQrToken() { return qrToken; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public String getCurrency() { return currency; }
    public UUID getPaymentId() { return paymentId; }
    public LocalDateTime getPaymentAuthorizedAt() { return paymentAuthorizedAt; }
    public CancellationReason getCancellationReason() { return cancellationReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
