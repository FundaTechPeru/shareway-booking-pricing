package com.fundatechperu.shareway.booking.domain.model;

/**
 * Why a booking was cancelled (bookings.cancellation_reason). Payments only knows three
 * reasons, so every system-side cancellation that is not a no-show travels to it as
 * NO_DRIVER_AVAILABLE (see {@link #paymentsReason()}).
 */
public enum CancellationReason {

    /** The passenger cancelled the booking (POST /api/v1/bookings/{id}/cancel). */
    PASSENGER_CANCELLED,

    /** The trip completed and the passenger never boarded. */
    NO_SHOW,

    /** The group was rejected, the trip was cancelled or the saga timed out looking for a driver. */
    NO_DRIVER_AVAILABLE,

    /** The saga timed out waiting for the payment authorization. */
    PAYMENT_TIMEOUT,

    /** Payments rejected the authorization. */
    PAYMENT_FAILED;

    /** Reason accepted by Payments: PASSENGER_CANCELLED, NO_SHOW or NO_DRIVER_AVAILABLE. */
    public String paymentsReason() {
        return switch (this) {
            case PASSENGER_CANCELLED -> PASSENGER_CANCELLED.name();
            case NO_SHOW -> NO_SHOW.name();
            default -> NO_DRIVER_AVAILABLE.name();
        };
    }
}
