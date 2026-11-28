package com.fundatechperu.shareway.booking.infrastructure.outbox;

import com.fundatechperu.shareway.booking.infrastructure.integration.InternalHttpClient.HttpResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.fundatechperu.shareway.booking.infrastructure.outbox.OutboxMessageType.*;
import static com.fundatechperu.shareway.booking.infrastructure.outbox.OutboxRelay.Outcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Retry rules of the outbox relay, per answer of the remote service. */
class OutboxRelayClassificationTest {

    private static OutboxRelay.Outcome classify(OutboxMessageType type, int status) {
        return OutboxRelay.classify(type, new HttpResult(status, "{}", null));
    }

    @Test
    void timeoutsAndConnectionErrorsAreRetried() {
        for (OutboxMessageType type : OutboxMessageType.values()) {
            assertEquals(RETRY, OutboxRelay.classify(type, new HttpResult(0, null, "Connection refused")));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 301, 302, 307, 401, 408, 429, 500, 502, 503, 504})
    void redirectsServerErrorsAndTheRetryableClientErrorsAreRetried(int status) {
        for (OutboxMessageType type : OutboxMessageType.values()) {
            assertEquals(RETRY, classify(type, status), type + " " + status);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 202, 204})
    void successIsDelivered(int status) {
        for (OutboxMessageType type : OutboxMessageType.values()) {
            assertEquals(DELIVERED, classify(type, status), type + " " + status);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {402, 404, 422})
    void aDeclinedAuthorizationCancelsTheBooking(int status) {
        assertEquals(PAYMENT_DECLINED, classify(PAYMENT_AUTHORIZATION, status));
    }

    @Test
    void aMissingPaymentForACancellationCountsAsDone() {
        assertEquals(DELIVERED, classify(BOOKING_CANCELLED, 404));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 405, 409, 415, 422})
    void theOtherClientErrorsAreRejectedForGood(int status) {
        assertEquals(REJECTED, classify(GROUP_CONFIRMED, status));
        assertEquals(REJECTED, classify(TRIP_COMPLETED, status));
    }

    @Test
    void theRemainingClientErrorsOfEachTypeAreRejected() {
        assertEquals(REJECTED, classify(PAYMENT_AUTHORIZATION, 400));
        assertEquals(REJECTED, classify(PAYMENT_AUTHORIZATION, 409));
        assertEquals(REJECTED, classify(BOOKING_CANCELLED, 400));
        assertEquals(REJECTED, classify(BOOKING_CANCELLED, 422));
    }
}
