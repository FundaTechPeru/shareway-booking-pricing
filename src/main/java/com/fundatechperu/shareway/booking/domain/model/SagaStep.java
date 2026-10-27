package com.fundatechperu.shareway.booking.domain.model;

/**
 * Steps of the booking saga (booking_sagas.current_step, stored in uppercase).
 */
public final class SagaStep {

    /** Booking created: waiting for the payment authorization. */
    public static final String AUTHORIZE_PAYMENT = "AUTHORIZE_PAYMENT";

    /** Payment authorized: waiting for the trip group to complete. */
    public static final String WAIT_GROUP = "WAIT_GROUP";

    /** Group completed: waiting for a driver to accept the proposal. */
    public static final String SEARCH_DRIVER = "SEARCH_DRIVER";

    private SagaStep() {
    }
}
