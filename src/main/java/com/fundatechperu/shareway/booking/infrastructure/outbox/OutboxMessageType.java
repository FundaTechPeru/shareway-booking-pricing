package com.fundatechperu.shareway.booking.infrastructure.outbox;

import com.fundatechperu.shareway.booking.infrastructure.integration.IntegrationProperties;

/** Kinds of outbox messages, each one with the service and path it is POSTed to. */
public enum OutboxMessageType {

    /** Payments: authorize the estimated fare of a new booking. */
    PAYMENT_AUTHORIZATION(Target.PAYMENTS, "/api/v1/internal/payments/authorizations"),

    /** Payments: a booking with an authorized payment was cancelled. */
    BOOKING_CANCELLED(Target.PAYMENTS, "/api/v1/internal/events/booking-cancelled"),

    /** Trip Execution: a driver accepted the group; create its trip with the confirmed passengers. */
    GROUP_CONFIRMED(Target.TRIP_EXECUTION, "/api/internal/grupos-confirmados"),

    /** Payments: capture the final amount of every completed booking of a trip. */
    TRIP_COMPLETED(Target.PAYMENTS, "/api/v1/internal/events/trip-completed");

    private enum Target { PAYMENTS, TRIP_EXECUTION }

    private final Target target;
    private final String path;

    OutboxMessageType(Target target, String path) {
        this.target = target;
        this.path = path;
    }

    /** Absolute URL, resolved when the message is sent so a changed base URL applies to pending messages. */
    public String url(IntegrationProperties properties) {
        String base = target == Target.PAYMENTS ? properties.paymentsUrl() : properties.tripExecutionUrl();
        return base + path;
    }

    public String path() {
        return path;
    }
}
