package com.fundatechperu.shareway.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.application.service.SagaTimeoutService;
import com.fundatechperu.shareway.booking.infrastructure.provider.SimulatedDepartureTimeProvider;
import com.fundatechperu.shareway.booking.infrastructure.security.BoardingCodeHasher;
import com.fundatechperu.shareway.booking.shared.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 2 against local PostgreSQL (no Testcontainers): the full lifecycle driven by the
 * in-memory event bus, the confirmation codes (only hashes stored), the closing fare, the
 * cancellation with penalty and the expired-saga compensation. The schema is reset per
 * class by {@link AbstractPostgresIntegrationTest} (Flyway clean + migrate, V2 seed
 * included) and the booking tables per test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Sprint2IntegrationTest extends AbstractPostgresIntegrationTest {

    private static final BigDecimal ESTIMATED_12KM = new BigDecimal("21.34");   // (5 + 1.2*12) * 1.10
    private static final BigDecimal FINAL_20KM = new BigDecimal("31.90");       // (5 + 1.2*20) * 1.10
    private static final String LATE_CANCEL_MESSAGE_PREFIX = "La reserva ya no puede cancelarse en su estado actual";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private SimulatedDepartureTimeProvider departureTimeProvider;

    @Autowired
    private SagaTimeoutService sagaTimeoutService;

    @Autowired
    private BoardingCodeHasher boardingCodeHasher;

    @Autowired
    private RecordingEventHandler recorder;

    @BeforeEach
    void isolateBookingData() {
        // fare_rules is kept: it carries the V2 seed (rule version 1) for the whole class.
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    fares,
                    penalties,
                    booking_sagas,
                    bookings,
                    seat_inventories
                CASCADE
                """);
        recorder.clear();
        departureTimeProvider.simulateOffsetMinutes(120);   // departures far away by default
    }

    /**
     * (1) Full flow: reserve -> payment authorized -> GroupCompleted -> ProposalAccepted ->
     * BoardingValidated -> TripStarted -> TripCompleted. Every state is verified, the
     * booking carries only the PIN/QR HASHES, the final fares row exists and the published
     * events (BookingConfirmed once with the clear codes, FinalFareCalculated) are checked.
     */
    @Test
    void fullLifecycleEndsWithHashesTheFinalFareAndThePublishedEvents() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = createBooking(groupId, passengerId);
        assertEquals("REQUESTED", statusOfBooking(bookingId));

        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), java.time.Instant.now(),
                bookingId, UUID.randomUUID()));
        eventPublisher.publish(new GroupCompleted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        assertEquals("GROUPED", statusOfBooking(bookingId));
        assertEquals("SEARCH_DRIVER", sagaStepOf(bookingId), "the saga must be looking for a driver");
        assertEquals("running", sagaStatusOf(bookingId));

        UUID driverId = UUID.randomUUID();
        eventPublisher.publish(new ProposalAccepted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, driverId));
        assertEquals("CONFIRMED", statusOfBooking(bookingId));
        assertEquals("completed", sagaStatusOf(bookingId), "the saga completes on confirmation");

        // only the HASHES live in bookings; the API never exposes the clear values
        Map<String, Object> codes = jdbcTemplate.queryForMap(
                "SELECT pin_hash, qr_token_hash FROM bookings WHERE booking_id = ?", bookingId);
        String pinHash = (String) codes.get("pin_hash");
        String qrTokenHash = (String) codes.get("qr_token_hash");
        assertNotNull(pinHash, "pin_hash must be stored on confirmation");
        assertNotNull(qrTokenHash, "qr_token_hash must be stored on confirmation");
        String bookingJson = getBooking(bookingId).getBody();
        assertFalse(bookingJson.toLowerCase().contains("pin"), "the API must not return the PIN");
        assertFalse(bookingJson.toLowerCase().contains("qr"), "the API must not return the QR token");

        // BookingConfirmed is published once, in clear, with codes that match the stored hashes
        List<BookingConfirmed> confirmedEvents = recorder.published(BookingConfirmed.class);
        assertEquals(1, confirmedEvents.size(), "BookingConfirmed must be published exactly once");
        BookingConfirmed confirmed = confirmedEvents.get(0);
        assertEquals(bookingId, confirmed.bookingId());
        assertEquals(groupId, confirmed.groupId());
        assertEquals(passengerId, confirmed.passengerId());
        assertEquals(driverId, confirmed.driverId());
        assertTrue(confirmed.boardingPin().matches("\\d{6}"), "the PIN must be 6 digits");
        assertTrue(boardingCodeHasher.matchesPin(confirmed.boardingPin(), pinHash),
                "the stored pin_hash must be the BCrypt of the published PIN");
        assertEquals(boardingCodeHasher.hashQrToken(confirmed.qrToken()), qrTokenHash,
                "the stored qr_token_hash must be the SHA-256 of the published token");

        eventPublisher.publish(new BoardingValidated(UUID.randomUUID(), java.time.Instant.now(), bookingId));
        assertEquals("BOARDED", statusOfBooking(bookingId));

        eventPublisher.publish(new TripStarted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        assertEquals("IN_PROGRESS", statusOfBooking(bookingId));

        eventPublisher.publish(new TripCompleted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, new BigDecimal("20")));
        assertEquals("COMPLETED", statusOfBooking(bookingId));

        // fares: estimated (12 km) + final (20 km), no duplicates
        Map<String, Object> finalFare = jdbcTemplate.queryForMap(
                "SELECT distance_km, amount, currency FROM fares WHERE booking_id = ? AND fare_type = 'final'",
                bookingId);
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) finalFare.get("distance_km")));
        assertEquals(0, FINAL_20KM.compareTo((BigDecimal) finalFare.get("amount")));
        assertEquals("PEN", finalFare.get("currency"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE booking_id = ? AND fare_type = 'final'",
                Integer.class, bookingId), "the final fare must exist exactly once");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE booking_id = ? AND fare_type = 'estimated'",
                Integer.class, bookingId), "the estimated fare must not be duplicated");

        // FinalFareCalculated with the amount, currency and rule version that were persisted
        List<FinalFareCalculated> closingEvents = recorder.published(FinalFareCalculated.class);
        assertEquals(1, closingEvents.size(), "FinalFareCalculated must be published exactly once");
        FinalFareCalculated closing = closingEvents.get(0);
        assertEquals(bookingId, closing.bookingId());
        assertEquals(0, FINAL_20KM.compareTo(closing.amount()));
        assertEquals("PEN", closing.currency());
        assertEquals(1, closing.ruleVersion());
    }

    /**
     * (2) Re-delivered events change nothing and raise no error: the booking stays grouped,
     * no fare is duplicated and nothing is republished.
     */
    @Test
    void duplicatedEventsAreIdempotentAndNeverFail() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID bookingId = createBooking(groupId, UUID.randomUUID());

        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), java.time.Instant.now(),
                bookingId, UUID.randomUUID()));
        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), java.time.Instant.now(),
                bookingId, UUID.randomUUID()));

        eventPublisher.publish(new GroupCompleted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        eventPublisher.publish(new GroupCompleted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        assertEquals("GROUPED", statusOfBooking(bookingId));
        assertEquals("SEARCH_DRIVER", sagaStepOf(bookingId));
        assertEquals("running", sagaStatusOf(bookingId));

        // events that do not match the current state are ignored, even when repeated
        eventPublisher.publish(new TripStarted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        eventPublisher.publish(new BoardingValidated(UUID.randomUUID(), java.time.Instant.now(), bookingId));
        eventPublisher.publish(new TripCompleted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, new BigDecimal("30")));
        eventPublisher.publish(new TripCompleted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, new BigDecimal("30")));

        assertEquals("GROUPED", statusOfBooking(bookingId), "no invalid transition may happen");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE fare_type = 'final'", Integer.class),
                "no final fare may exist for a booking that never travelled");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE booking_id = ?", Integer.class, bookingId),
                "the estimated fare must not be duplicated");
        assertTrue(recorder.published(BookingConfirmed.class).isEmpty());
        assertTrue(recorder.published(FinalFareCalculated.class).isEmpty());
    }

    /**
     * (3) An event that does not fit the state machine (TripCompleted over a requested
     * booking) leaves the data untouched: no transition, no fare, no event.
     */
    @Test
    void invalidTransitionsAreIgnoredWithoutDamagingTheData() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID bookingId = createBooking(groupId, UUID.randomUUID());

        eventPublisher.publish(new TripCompleted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, new BigDecimal("10")));
        assertEquals("REQUESTED", statusOfBooking(bookingId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE fare_type = 'final'", Integer.class));

        eventPublisher.publish(new BoardingValidated(UUID.randomUUID(), java.time.Instant.now(), bookingId));
        assertEquals("REQUESTED", statusOfBooking(bookingId));

        eventPublisher.publish(new ProposalAccepted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, UUID.randomUUID()));
        assertEquals("REQUESTED", statusOfBooking(bookingId));
        assertNull(jdbcTemplate.queryForObject("SELECT pin_hash FROM bookings WHERE booking_id = ?",
                String.class, bookingId), "codes are only generated on confirmation");
        assertTrue(recorder.published(BookingConfirmed.class).isEmpty());
        assertTrue(recorder.published(FinalFareCalculated.class).isEmpty());
    }

    /**
     * (4) Cancellation matrix: more than 30 minutes releases the seat without penalty; less
     * than 30 minutes creates the 20 % penalty (4.27 = 20 % of 21.34, above the 1.00 floor);
     * cancelling twice never releases a second seat nor charges twice.
     */
    @Test
    void cancellationChargesOnlyInsideTheThirtyMinuteWindowAndIsIdempotent() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID earlyPassenger = UUID.randomUUID();
        UUID latePassenger = UUID.randomUUID();

        // (a) more than 30 minutes ahead: no penalty. The booking is created alone, so its
        // estimate is the solo-rider fare of 12 km (21.34).
        UUID earlyBooking = createBooking(groupId, earlyPassenger);
        assertEquals(3, availableSeats(groupId), "one of four seats is held");
        departureTimeProvider.simulateOffsetMinutes(120);
        ResponseEntity<String> early = cancel(earlyBooking, earlyPassenger);
        assertEquals(200, early.getStatusCode().value(), String.valueOf(early.getBody()));
        JsonNode earlyBody = objectMapper.readTree(early.getBody());
        assertEquals(earlyBooking.toString(), earlyBody.get("bookingId").asText());
        assertEquals("CANCELLED", earlyBody.get("status").asText());
        assertTrue(earlyBody.get("penaltyAmount").isNull());
        assertEquals(4, availableSeats(groupId), "the seat must come back");
        assertEquals(0, penaltyCount(), "no penalty may be created far from the departure");

        // (b) less than 30 minutes ahead: 20 % penalty of the 21.34 estimate
        UUID lateBooking = createBooking(groupId, latePassenger);
        assertEquals(3, availableSeats(groupId), "the late booking holds the only seat again");
        departureTimeProvider.simulateOffsetMinutes(10);
        ResponseEntity<String> late = cancel(lateBooking, latePassenger);
        assertEquals(200, late.getStatusCode().value(), String.valueOf(late.getBody()));
        JsonNode lateBody = objectMapper.readTree(late.getBody());
        assertEquals("CANCELLED", lateBody.get("status").asText());
        assertEquals(0, new BigDecimal("4.27").compareTo(lateBody.get("penaltyAmount").decimalValue()),
                "20 % of the 21.34 estimate, HALF_UP, above the 1.00 floor");
        assertEquals("PEN", lateBody.get("currency").asText());
        assertEquals(4, availableSeats(groupId), "the seat must come back");
        assertEquals("compensated", sagaStatusOf(lateBooking));

        Map<String, Object> penalty = jdbcTemplate.queryForMap(
                "SELECT amount, reason FROM penalties WHERE booking_id = ?", lateBooking);
        assertEquals(0, new BigDecimal("4.27").compareTo((BigDecimal) penalty.get("amount")));
        assertEquals("LATE_CANCELLATION", penalty.get("reason"));

        // (c) cancelling again is a 200 with no changes: no second seat, no second penalty
        ResponseEntity<String> again = cancel(lateBooking, latePassenger);
        assertEquals(200, again.getStatusCode().value(), String.valueOf(again.getBody()));
        assertTrue(objectMapper.readTree(again.getBody()).get("penaltyAmount").isNull(),
                "the idempotent answer must not charge a second penalty");
        assertEquals(4, availableSeats(groupId), "available_seats must never exceed capacity");
        assertEquals(1, penaltyCount(), "one booking can never carry two penalties");

        List<BookingCancelled> cancellations = recorder.published(BookingCancelled.class);
        assertEquals(2, cancellations.size(), "one BookingCancelled per booking actually cancelled");
        assertFalse(cancellations.get(0).penaltyApplicable());
        assertTrue(cancellations.get(1).penaltyApplicable());
    }

    /**
     * (5) A boarded booking cannot be cancelled: 409 with a clear message and no data
     * changes.
     */
    @Test
    void cancellingABoardedBookingAnswers409() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = createBoardedBooking(groupId, passengerId);
        assertEquals(3, availableSeats(groupId), "the seat is still held by the trip");

        ResponseEntity<String> response = cancel(bookingId, passengerId);

        assertEquals(409, response.getStatusCode().value(), String.valueOf(response.getBody()));
        assertTrue(messageOf(response).startsWith(LATE_CANCEL_MESSAGE_PREFIX),
                "the 409 must carry a clear message, got: " + messageOf(response));
        assertEquals("BOARDED", statusOfBooking(bookingId));
        assertEquals(3, availableSeats(groupId), "the seat must not be released");
        assertEquals(0, penaltyCount());
        assertTrue(recorder.published(BookingCancelled.class).isEmpty());
    }

    /**
     * (6) Expired saga: the compensation cancels the booking, releases the seat and
     * compensates the saga, both for a payment that never arrived (PAYMENT_TIMEOUT) and
     * for a group that never completed (NO_DRIVER_AVAILABLE).
     */
    @Test
    void theTimeoutJobCompensatesExpiredSagasAndReleasesTheSeats() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID paymentBooking = createBooking(groupId, UUID.randomUUID());
        expireSagaOf(paymentBooking);

        assertEquals(1, sagaTimeoutService.compensateExpiredSagas());
        assertEquals("CANCELLED", statusOfBooking(paymentBooking), "a PAYMENT_TIMEOUT saga must cancel the booking");
        assertEquals("compensated", sagaStatusOf(paymentBooking));
        assertEquals(4, availableSeats(groupId), "the seat must be released");

        // a booking that paid but whose group never completed expires with NO_DRIVER_AVAILABLE
        UUID groupBooking = createBooking(groupId, UUID.randomUUID());
        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), java.time.Instant.now(),
                groupBooking, UUID.randomUUID()));
        expireSagaOf(groupBooking);

        assertEquals(1, sagaTimeoutService.compensateExpiredSagas());
        assertEquals("CANCELLED", statusOfBooking(groupBooking));
        assertEquals(4, availableSeats(groupId), "both seats must be back");
        assertEquals(2, recorder.published(BookingCancelled.class).size());

        // a second run finds nothing to compensate
        assertEquals(0, sagaTimeoutService.compensateExpiredSagas());
    }

    /**
     * (7) RouteUpdated recalculates the estimated amount: the 12 km estimate (21.34) is
     * rewritten in place with the new 20 km distance (31.90), never duplicated.
     */
    @Test
    void routeUpdatedRecalculatesTheEstimatedFareInPlace() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        UUID bookingId = createBooking(groupId, UUID.randomUUID());
        assertEquals(0, ESTIMATED_12KM.compareTo(estimatedAmountOf(bookingId)),
                "the booking starts with the 12 km estimate");

        eventPublisher.publish(new RouteUpdated(UUID.randomUUID(), java.time.Instant.now(),
                groupId, new BigDecimal("20")));

        assertEquals(0, FINAL_20KM.compareTo(estimatedAmountOf(bookingId)),
                "the estimate must be recalculated with the new distance");
        Map<String, Object> fare = jdbcTemplate.queryForMap(
                "SELECT distance_km FROM fares WHERE booking_id = ? AND fare_type = 'estimated'", bookingId);
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) fare.get("distance_km")));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fares WHERE booking_id = ? AND fare_type = 'estimated'",
                Integer.class, bookingId), "the row must be updated, never duplicated");
        assertEquals("REQUESTED", statusOfBooking(bookingId), "the booking itself does not move");
    }

    /**
     * (8) Concurrency: 20 simultaneous cancellations of 20 distinct bookings never leave
     * available_seats above capacity (they end exactly at capacity).
     */
    @Test
    void twentySimultaneousCancellationsNeverExceedTheCapacity() throws Exception {
        int capacity = 20;
        UUID groupId = insertSeatInventory(capacity, capacity);

        List<UUID> bookingIds = new ArrayList<>();
        List<UUID> passengerIds = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            UUID passengerId = UUID.randomUUID();
            passengerIds.add(passengerId);
            bookingIds.add(createBooking(groupId, passengerId));
        }
        assertEquals(0, availableSeats(groupId), "every booking holds one seat");

        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(capacity);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>(capacity);
            for (int i = 0; i < capacity; i++) {
                UUID bookingId = bookingIds.get(i);
                UUID passengerId = passengerIds.get(i);
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return cancel(bookingId, passengerId);
                }));
            }
            startLine.countDown();

            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(2, TimeUnit.MINUTES);
                assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(capacity, availableSeats(groupId),
                "every seat must be back and none may exceed the capacity");
        assertEquals(capacity, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE group_id = ? AND status = 'cancelled'",
                Integer.class, groupId));
    }

    // --------------------------------------------------------------------------- helpers

    private UUID createBooking(UUID groupId, UUID passengerId) {
        ResponseEntity<String> response = postBooking(groupId, passengerId);
        assertEquals(202, response.getStatusCode().value(), String.valueOf(response.getBody()));
        return UUID.fromString(jsonOf(response).get("bookingId").asText());
    }

    /** Drives a booking to boarded: payment, group, accepted proposal and boarding scan. */
    private UUID createBoardedBooking(UUID groupId, UUID passengerId) {
        UUID bookingId = createBooking(groupId, passengerId);
        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), java.time.Instant.now(),
                bookingId, UUID.randomUUID()));
        eventPublisher.publish(new GroupCompleted(UUID.randomUUID(), java.time.Instant.now(), groupId));
        eventPublisher.publish(new ProposalAccepted(UUID.randomUUID(), java.time.Instant.now(),
                groupId, UUID.randomUUID()));
        eventPublisher.publish(new BoardingValidated(UUID.randomUUID(), java.time.Instant.now(), bookingId));
        assertEquals("BOARDED", statusOfBooking(bookingId));
        return bookingId;
    }

    private ResponseEntity<String> postBooking(UUID groupId, UUID passengerId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-User-Id", passengerId.toString());
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, String> payload = Map.of("groupId", groupId.toString(),
                "requestId", UUID.randomUUID().toString());
        return rest.postForEntity("/bookings", new HttpEntity<>(payload, headers), String.class);
    }

    private ResponseEntity<String> getBooking(UUID bookingId) {
        return rest.exchange("/bookings/{bookingId}", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class, bookingId);
    }

    private ResponseEntity<String> cancel(UUID bookingId, UUID actorId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-User-Id", actorId.toString());
        return rest.exchange("/bookings/{bookingId}/cancel", HttpMethod.POST,
                new HttpEntity<>(headers), String.class, bookingId);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode jsonOf(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new AssertionError("Body is not JSON: " + response.getBody(), exception);
        }
    }

    private String messageOf(ResponseEntity<String> response) {
        return jsonOf(response).get("message").asText();
    }

    private String statusOfBooking(UUID bookingId) {
        return jsonOf(getBooking(bookingId)).get("status").asText();
    }

    private BigDecimal estimatedAmountOf(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT amount FROM fares WHERE booking_id = ? AND fare_type = 'estimated'",
                BigDecimal.class, bookingId);
    }

    private String sagaStepOf(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_step FROM booking_sagas WHERE booking_id = ?", String.class, bookingId);
    }

    private String sagaStatusOf(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM booking_sagas WHERE booking_id = ?", String.class, bookingId);
    }

    private void expireSagaOf(UUID bookingId) {
        jdbcTemplate.update("UPDATE booking_sagas SET deadline_at = CURRENT_TIMESTAMP "
                + "- INTERVAL '1 minute' WHERE booking_id = ?", bookingId);
    }

    private int penaltyCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM penalties", Integer.class);
        return count == null ? -1 : count;
    }

    private UUID insertSeatInventory(int capacity, int availableSeats) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO seat_inventories (group_id, capacity, available_seats) VALUES (?, ?, ?)",
                groupId, capacity, availableSeats);
        return groupId;
    }

    private int availableSeats(UUID groupId) {
        Integer seats = jdbcTemplate.queryForObject(
                "SELECT available_seats FROM seat_inventories WHERE group_id = ?", Integer.class, groupId);
        assertNotNull(seats, "seat inventory must exist for " + groupId);
        return seats;
    }

    // ------------------------------------------------------------------ event recording

    /** Records every published event so the tests can assert on them. */
    static class RecordingEventHandler implements EventHandler {
        private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean supports(Object event) {
            return true;
        }

        @Override
        public void onEvent(Object event) {
            events.add(event);
        }

        <T> List<T> published(Class<T> type) {
            List<T> found = new ArrayList<>();
            synchronized (events) {
                events.forEach(event -> {
                    if (type.isInstance(event)) found.add(type.cast(event));
                });
            }
            return found;
        }

        void clear() {
            events.clear();
        }
    }

    @TestConfiguration
    static class EventRecorderConfiguration {
        @Bean
        RecordingEventHandler recordingEventHandler() {
            return new RecordingEventHandler();
        }
    }
}
