package com.fundatechperu.shareway.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.infrastructure.outbox.OutboxRelay;
import com.fundatechperu.shareway.booking.infrastructure.security.BoardingCodeHasher;
import com.fundatechperu.shareway.booking.infrastructure.security.InternalApiKey;
import com.fundatechperu.shareway.booking.support.FakeHttpServer;
import com.fundatechperu.shareway.booking.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The outbox against simulated Payments and Trip Execution servers: the payment authorization
 * (accepted and declined), the retry rules (order kept on retryable errors, permanent 4xx
 * skipped), the cancellation, the confirmed group with the SHA-256 of the codes, the completed
 * trip with the amount capped at the authorization, and the whole flow driven through
 * POST /api/v1/internal/events. The relay is invoked directly (it is off in the test profile).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String AUTHORIZATIONS = "/api/v1/internal/payments/authorizations";
    private static final String BOOKING_CANCELLED = "/api/v1/internal/events/booking-cancelled";
    private static final String TRIP_COMPLETED = "/api/v1/internal/events/trip-completed";
    private static final String GROUPS_CONFIRMED = "/api/internal/grupos-confirmados";
    private static final BigDecimal ESTIMATED_12KM = new BigDecimal("21.34");   // (5 + 1.2*12) * 1.10

    private static final FakeHttpServer PAYMENTS = new FakeHttpServer();
    private static final FakeHttpServer TRIP_EXECUTION = new FakeHttpServer();

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("app.integration.payments-url", PAYMENTS::url);
        registry.add("app.integration.trip-execution-url", TRIP_EXECUTION::url);
    }

    @AfterAll
    void stopServers() {
        PAYMENTS.close();
        TRIP_EXECUTION.close();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private BoardingCodeHasher hasher;

    @BeforeEach
    void isolate() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE fares, penalties, booking_sagas, bookings, seat_inventories,
                    processed_events, outbox_messages
                CASCADE
                """);
        PAYMENTS.reset();
        TRIP_EXECUTION.reset();
        PAYMENTS.respondByDefault(AUTHORIZATIONS, request -> new FakeHttpServer.Response(201,
                "{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"AUTHORIZED\"}"));
    }

    // --------------------------------------------------------- payment authorization

    @Test
    void anAcceptedAuthorizationAdvancesTheSagaWithThePaymentRecorded() throws Exception {
        UUID groupId = openGroup(4);
        UUID passengerId = UUID.randomUUID();
        UUID paymentMethodId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PAYMENTS.enqueue(AUTHORIZATIONS, 201, "{\"id\":\"" + paymentId + "\",\"status\":\"AUTHORIZED\"}");

        UUID bookingId = book(groupId, passengerId, paymentMethodId);
        assertEquals("AUTHORIZE_PAYMENT", sagaStep(bookingId));
        assertEquals(1, relay.relayPending());

        List<FakeHttpServer.Request> calls = PAYMENTS.requestsTo(AUTHORIZATIONS);
        assertEquals(1, calls.size());
        assertEquals("POST", calls.get(0).method());
        assertEquals(InternalApiKey.DEVELOPMENT_API_KEY, calls.get(0).apiKey(), "the internal key travels");
        JsonNode body = objectMapper.readTree(calls.get(0).body());
        assertEquals(bookingId.toString(), body.get("bookingId").asText());
        assertEquals(passengerId.toString(), body.get("passengerId").asText());
        assertEquals(paymentMethodId.toString(), body.get("paymentMethodId").asText());
        assertEquals(0, ESTIMATED_12KM.compareTo(body.get("amount").decimalValue()));
        assertEquals("PEN", body.get("currency").asText());

        assertEquals("WAIT_GROUP", sagaStep(bookingId), "PaymentAuthorized was published locally");
        assertEquals(paymentId, jdbcTemplate.queryForObject(
                "SELECT payment_id FROM bookings WHERE booking_id = ?", UUID.class, bookingId));
        assertNotNull(messageColumn("PAYMENT_AUTHORIZATION", "processed_at"));
        assertEquals(0, relay.relayPending(), "nothing is sent twice");
    }

    @Test
    void withoutPaymentMethodTheFieldIsOmittedSoPaymentsPicksTheCard() throws Exception {
        UUID bookingId = book(openGroup(4), UUID.randomUUID(), null);
        relay.relayPending();

        JsonNode body = objectMapper.readTree(PAYMENTS.requestsTo(AUTHORIZATIONS).get(0).body());
        assertEquals(bookingId.toString(), body.get("bookingId").asText());
        assertFalse(body.has("paymentMethodId"));
    }

    @Test
    void aDeclinedAuthorizationCancelsTheBookingAndSettlesTheMessage() {
        for (int status : List.of(402, 404, 422)) {
            UUID groupId = openGroup(2);
            PAYMENTS.enqueue(AUTHORIZATIONS, status, "{\"status\":" + status + ",\"message\":\"Card declined\"}");
            UUID bookingId = book(groupId, UUID.randomUUID(), null);
            assertEquals(1, availableSeats(groupId));

            assertEquals(1, relay.relayPending(), "HTTP " + status + " settles the message");

            assertEquals("cancelled", bookingStatus(bookingId), "HTTP " + status);
            assertEquals("PAYMENT_FAILED", jdbcTemplate.queryForObject(
                    "SELECT cancellation_reason FROM bookings WHERE booking_id = ?", String.class, bookingId));
            assertEquals("compensated", sagaStatus(bookingId));
            assertEquals(2, availableSeats(groupId), "the seat comes back");
        }
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_messages WHERE processed_at IS NULL"));
        assertTrue(PAYMENTS.requestsTo(BOOKING_CANCELLED).isEmpty(), "nothing was authorized: nothing to release");
    }

    // ------------------------------------------------------------------- retry rules

    @Test
    void retryableErrorsStopTheBatchToKeepTheOrderAndBackOff() {
        UUID groupId = openGroup(4);
        UUID first = book(groupId, UUID.randomUUID(), null);
        UUID second = book(groupId, UUID.randomUUID(), null);

        for (int status : List.of(500, 503, 401, 408, 429, 302)) {
            jdbcTemplate.update("UPDATE outbox_messages SET next_attempt_at = now() - INTERVAL '1 second'");
            PAYMENTS.reset();
            PAYMENTS.enqueue(AUTHORIZATIONS, status, "{}");

            assertEquals(0, relay.relayPending(), "HTTP " + status + " is retried");
            assertEquals(1, PAYMENTS.requestsTo(AUTHORIZATIONS).size(), "HTTP " + status + ": the batch stops");
            assertEquals(first.toString(), bookingIdOfRequest(0), "the oldest message goes first");
        }
        assertEquals(6, count("SELECT attempts FROM outbox_messages WHERE aggregate_id = '" + first + "'"));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT next_attempt_at > now() FROM outbox_messages WHERE aggregate_id = ?", Boolean.class, first),
                "the message waits before the next attempt");
        assertTrue(jdbcTemplate.queryForObject("SELECT last_error FROM outbox_messages WHERE aggregate_id = ?",
                String.class, first).startsWith("HTTP 302"));

        // while it backs off nothing else is sent, so the order is kept
        PAYMENTS.reset();
        assertEquals(0, relay.relayPending());
        assertTrue(PAYMENTS.requests().isEmpty());
        assertEquals("AUTHORIZE_PAYMENT", sagaStep(first));
        assertEquals("AUTHORIZE_PAYMENT", sagaStep(second), "the second message never overtook the first");
        assertEquals(0, count("SELECT attempts FROM outbox_messages WHERE aggregate_id = '" + second + "'"));
    }

    @Test
    void thePermanentClientErrorsAreMarkedFailedAndTheBatchGoesOn() {
        UUID groupId = openGroup(4);
        UUID rejected = book(groupId, UUID.randomUUID(), null);
        UUID accepted = book(groupId, UUID.randomUUID(), null);
        PAYMENTS.enqueue(AUTHORIZATIONS, 400, "{\"message\":\"amount must be positive\"}");

        assertEquals(2, relay.relayPending());

        assertEquals(2, PAYMENTS.requestsTo(AUTHORIZATIONS).size(), "the batch goes on after a 400");
        assertNotNull(jdbcTemplate.queryForObject("SELECT failed_at FROM outbox_messages WHERE aggregate_id = ?",
                Object.class, rejected));
        assertTrue(jdbcTemplate.queryForObject("SELECT last_error FROM outbox_messages WHERE aggregate_id = ?",
                String.class, rejected).contains("HTTP 400"));
        assertEquals("AUTHORIZE_PAYMENT", sagaStep(rejected), "the saga timeout will compensate it");
        assertEquals("WAIT_GROUP", sagaStep(accepted));
    }

    @Test
    void backoffDoublesUpToFiveMinutes() {
        // test profile: 1 s initial backoff, 5 min cap
        assertEquals(java.time.Duration.ofSeconds(1), relay.backoff(1));
        assertEquals(java.time.Duration.ofSeconds(2), relay.backoff(2));
        assertEquals(java.time.Duration.ofSeconds(256), relay.backoff(9));
        assertEquals(java.time.Duration.ofMinutes(5), relay.backoff(10));
        assertEquals(java.time.Duration.ofMinutes(5), relay.backoff(50));
    }

    // --------------------------------------------------------------------- cancellation

    @Test
    void cancellingABookingWithAnAuthorizedPaymentNotifiesPayments() throws Exception {
        UUID groupId = openGroup(4);
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = book(groupId, passengerId, null);
        relay.relayPending();   // authorized

        assertEquals(200, cancel(bookingId, passengerId).getStatusCode().value());
        PAYMENTS.enqueue(BOOKING_CANCELLED, 404, "{\"message\":\"Payment not found\"}");
        assertEquals(1, relay.relayPending(), "a 404 from Payments counts as done");

        List<FakeHttpServer.Request> calls = PAYMENTS.requestsTo(BOOKING_CANCELLED);
        assertEquals(1, calls.size());
        JsonNode body = objectMapper.readTree(calls.get(0).body());
        assertEquals(bookingId.toString(), body.get("bookingId").asText());
        assertEquals("PASSENGER_CANCELLED", body.get("reason").asText());
        Instant cancelledAt = Instant.parse(body.get("cancelledAt").asText());
        Instant departure = Instant.parse(body.get("scheduledDepartureAt").asText());
        assertTrue(departure.isAfter(cancelledAt), "the simulated departure is 120 minutes ahead");
        assertNotNull(messageColumn("BOOKING_CANCELLED", "processed_at"));
    }

    /** Cancelled while its authorization was still queued: Payments authorizes it, so it is released. */
    @Test
    void aBookingCancelledBeforeItsAuthorizationIsReleasedInPayments() throws Exception {
        UUID groupId = openGroup(4);
        UUID passengerId = UUID.randomUUID();
        UUID bookingId = book(groupId, passengerId, null);
        assertEquals(200, cancel(bookingId, passengerId).getStatusCode().value());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_messages WHERE type = 'BOOKING_CANCELLED'"),
                "nothing was authorized yet");

        assertEquals(1, relay.relayPending(), "the authorization still goes out");
        assertEquals(1, relay.relayPending(), "then the release queued by PaymentAuthorized");
        List<FakeHttpServer.Request> cancellations = PAYMENTS.requestsTo(BOOKING_CANCELLED);
        assertEquals(1, cancellations.size());
        JsonNode body = objectMapper.readTree(cancellations.get(0).body());
        assertEquals(bookingId.toString(), body.get("bookingId").asText());
        assertEquals("PASSENGER_CANCELLED", body.get("reason").asText());
        assertEquals("cancelled", bookingStatus(bookingId));
        assertEquals(0, relay.relayPending(), "nothing is reported twice");
    }

    // ------------------------------------------------------------------- full flow

    /**
     * Group opened -> booking -> payment authorized -> group completed -> proposal accepted
     * (Trip Execution receives the hashes) -> boarding -> trip started -> trip completed
     * (Payments captures at most the authorized amount).
     */
    @Test
    void theWholeFlowReachesPaymentsAndTripExecution() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();
        UUID noShowPassenger = UUID.randomUUID();

        internalEvent("TripGroupOpened", Map.of("groupId", groupId, "capacity", 2));
        UUID bookingId = book(groupId, passengerId, null);
        UUID noShowId = book(groupId, noShowPassenger, null);
        assertEquals(2, relay.relayPending(), "both authorizations are sent");
        assertEquals("WAIT_GROUP", sagaStep(bookingId));

        internalEvent("GroupCompleted", Map.of("groupId", groupId));
        assertEquals("grouped", bookingStatus(bookingId));

        internalEvent("ProposalAccepted", Map.of("groupId", groupId, "driverId", driverId));
        assertEquals("confirmed", bookingStatus(bookingId));
        JsonNode ownerView = objectMapper.readTree(getBooking(bookingId, passengerId).getBody());
        String pin = ownerView.get("boardingPin").asText();
        String qrToken = ownerView.get("qrToken").asText();
        assertTrue(pin.matches("\\d{6}"));

        assertEquals(1, relay.relayPending(), "one GroupConfirmed message for the group");
        List<FakeHttpServer.Request> groupCalls = TRIP_EXECUTION.requestsTo(GROUPS_CONFIRMED);
        assertEquals(1, groupCalls.size());
        assertEquals(InternalApiKey.DEVELOPMENT_API_KEY, groupCalls.get(0).apiKey());
        JsonNode group = objectMapper.readTree(groupCalls.get(0).body());
        assertDoesNotThrow(() -> UUID.fromString(group.get("idEvento").asText()));
        assertEquals(groupId.toString(), group.get("grupoId").asText());
        assertEquals(driverId.toString(), group.get("conductorId").asText());
        assertEquals(2, group.get("pasajeros").size());
        JsonNode passenger = findBy(group.get("pasajeros"), "reservaId", bookingId.toString());
        assertEquals(passengerId.toString(), passenger.get("pasajeroId").asText());
        assertEquals(hasher.sha256Hex(qrToken), passenger.get("codigoHash").asText(), "SHA-256 of the QR token");
        assertEquals(hasher.sha256Hex(pin), passenger.get("pinHash").asText(), "SHA-256 of the PIN");
        assertTrue(passenger.get("codigoHash").asText().matches("[0-9a-f]{64}"), "lowercase hexadecimal");
        assertTrue(passenger.get("pinHash").asText().matches("[0-9a-f]{64}"), "lowercase hexadecimal");

        internalEvent("BoardingValidated", Map.of("bookingId", bookingId, "tripId", tripId));
        assertEquals("boarded", bookingStatus(bookingId));
        internalEvent("TripStarted", Map.of("groupId", groupId, "tripId", tripId));
        assertEquals("in_progress", bookingStatus(bookingId));

        // 60 km split by the 2 held seats: (5 + 1.2*60) / 2 = 38.50 + 10 % = 42.35, above the
        // 21.34 authorized when the booking was alone in the group
        internalEvent("TripCompleted", Map.of("groupId", groupId, "tripId", tripId, "driverId", driverId,
                "completedAt", "2026-11-27T16:00:00Z", "distanceKm", 60,
                "boardedBookingIds", List.of(bookingId.toString())));
        assertEquals("completed", bookingStatus(bookingId));
        assertEquals("cancelled", bookingStatus(noShowId));
        BigDecimal finalFare = jdbcTemplate.queryForObject(
                "SELECT amount FROM fares WHERE booking_id = ? AND fare_type = 'final'", BigDecimal.class, bookingId);
        BigDecimal authorized = jdbcTemplate.queryForObject(
                "SELECT estimated_amount FROM bookings WHERE booking_id = ?", BigDecimal.class, bookingId);
        assertTrue(finalFare.compareTo(authorized) > 0, "60 km cost more than the 12 km estimate");

        assertEquals(2, relay.relayPending(), "trip-completed and the no-show cancellation");
        List<FakeHttpServer.Request> tripCalls = PAYMENTS.requestsTo(TRIP_COMPLETED);
        assertEquals(1, tripCalls.size(), "ONE trip-completed message");
        JsonNode trip = objectMapper.readTree(tripCalls.get(0).body());
        assertEquals(tripId.toString(), trip.get("tripId").asText());
        assertEquals(driverId.toString(), trip.get("driverId").asText());
        assertEquals(Instant.parse("2026-11-27T16:00:00Z"), Instant.parse(trip.get("completedAt").asText()));
        assertEquals(1, trip.get("bookings").size(), "only the completed booking is charged");
        JsonNode charge = trip.get("bookings").get(0);
        assertEquals(bookingId.toString(), charge.get("bookingId").asText());
        assertEquals(0, authorized.compareTo(charge.get("finalAmount").decimalValue()),
                "finalAmount = min(final fare, authorized amount)");

        JsonNode noShow = objectMapper.readTree(PAYMENTS.requestsTo(BOOKING_CANCELLED).get(0).body());
        assertEquals(noShowId.toString(), noShow.get("bookingId").asText());
        assertEquals("NO_SHOW", noShow.get("reason").asText());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_messages WHERE processed_at IS NULL"));
    }

    @Test
    void aCheaperFinalFareIsChargedAsIs() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        internalEvent("TripGroupOpened", Map.of("groupId", groupId, "capacity", 4));
        UUID bookingId = book(groupId, UUID.randomUUID(), null);
        relay.relayPending();
        internalEvent("GroupCompleted", Map.of("groupId", groupId));
        internalEvent("ProposalAccepted", Map.of("groupId", groupId, "driverId", driverId));
        relay.relayPending();
        internalEvent("BoardingValidated", Map.of("bookingId", bookingId, "tripId", tripId));
        internalEvent("TripStarted", Map.of("groupId", groupId, "tripId", tripId));
        internalEvent("TripCompleted", Map.of("groupId", groupId, "tripId", tripId, "driverId", driverId,
                "completedAt", Instant.now().toString(), "distanceKm", 5,
                "boardedBookingIds", List.of(bookingId.toString())));

        relay.relayPending();
        JsonNode charge = objectMapper.readTree(PAYMENTS.requestsTo(TRIP_COMPLETED).get(0).body())
                .get("bookings").get(0);
        // (5 + 1.2*5) * 1.10 = 12.10, below the 21.34 authorized
        assertEquals(0, new BigDecimal("12.10").compareTo(charge.get("finalAmount").decimalValue()));
    }

    // --------------------------------------------------------------------------- helpers

    private void internalEvent(String type, Map<String, ?> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID().toString());
        body.put("occurredAt", Instant.now().toString());
        body.put("type", type);
        fields.forEach((name, value) -> body.put(name, value instanceof UUID uuid ? uuid.toString() : value));
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Api-Key", InternalApiKey.DEVELOPMENT_API_KEY);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/internal/events",
                new HttpEntity<>(body, headers), String.class);
        assertEquals(202, response.getStatusCode().value(), response.getBody());
    }

    private UUID openGroup(int capacity) {
        UUID groupId = UUID.randomUUID();
        internalEvent("TripGroupOpened", Map.of("groupId", groupId, "capacity", capacity));
        return groupId;
    }

    private UUID book(UUID groupId, UUID passengerId, UUID paymentMethodId) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(TestTokens.passenger(passengerId));
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId.toString());
        payload.put("requestId", UUID.randomUUID().toString());
        if (paymentMethodId != null) payload.put("paymentMethodId", paymentMethodId.toString());
        ResponseEntity<String> response = rest.postForEntity("/api/v1/bookings", new HttpEntity<>(payload, headers),
                String.class);
        assertEquals(202, response.getStatusCode().value(), response.getBody());
        try {
            return UUID.fromString(objectMapper.readTree(response.getBody()).get("bookingId").asText());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ResponseEntity<String> getBooking(UUID bookingId, UUID passengerId) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(TestTokens.passenger(passengerId));
        return rest.exchange("/api/v1/bookings/{id}", HttpMethod.GET, new HttpEntity<>(headers), String.class,
                bookingId);
    }

    private ResponseEntity<String> cancel(UUID bookingId, UUID passengerId) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(TestTokens.passenger(passengerId));
        return rest.exchange("/api/v1/bookings/{id}/cancel", HttpMethod.POST, new HttpEntity<>(headers),
                String.class, bookingId);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String bookingIdOfRequest(int index) {
        try {
            return objectMapper.readTree(PAYMENTS.requestsTo(AUTHORIZATIONS).get(index).body()).get("bookingId").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static JsonNode findBy(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + array);
    }

    private Object messageColumn(String type, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM outbox_messages WHERE type = ? "
                + "ORDER BY sequence_number LIMIT 1", Object.class, type);
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? -1 : count;
    }

    private String bookingStatus(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId);
    }

    private String sagaStep(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT current_step FROM booking_sagas WHERE booking_id = ?",
                String.class, bookingId);
    }

    private String sagaStatus(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM booking_sagas WHERE booking_id = ?",
                String.class, bookingId);
    }

    private int availableSeats(UUID groupId) {
        Integer seats = jdbcTemplate.queryForObject("SELECT available_seats FROM seat_inventories WHERE group_id = ?",
                Integer.class, groupId);
        return seats == null ? -1 : seats;
    }
}
