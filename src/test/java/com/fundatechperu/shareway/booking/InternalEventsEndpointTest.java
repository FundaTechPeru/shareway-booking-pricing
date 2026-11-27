package com.fundatechperu.shareway.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.infrastructure.security.InternalApiKey;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import com.fundatechperu.shareway.booking.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POST /api/v1/internal/events against local PostgreSQL: the X-Internal-Api-Key guard, every
 * event type of Trip Planning and Trip Execution, the idempotency by eventId and the rule that
 * a valid event that changes nothing still answers 202 (the senders treat 4xx as final).
 * The outbox relay is off in the test profile: queued messages stay in outbox_messages.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalEventsEndpointTest extends AbstractPostgresIntegrationTest {

    private static final String EVENTS = "/api/v1/internal/events";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublisher eventPublisher;

    @BeforeEach
    void isolateData() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE fares, penalties, booking_sagas, bookings, seat_inventories,
                    processed_events, outbox_messages
                CASCADE
                """);
    }

    // ------------------------------------------------------------------------ security

    @Test
    void theInternalKeyIsRequiredAndComparedExactly() throws Exception {
        String body = event("TripGroupOpened", Map.of("groupId", UUID.randomUUID(), "capacity", 4));

        ResponseEntity<String> missing = post(body, null);
        assertEquals(401, missing.getStatusCode().value());
        assertEquals("Missing or invalid internal API key", json(missing).get("message").asText());
        assertEquals(401, post(body, UUID.randomUUID().toString()).getStatusCode().value());
        assertEquals(401, post(body, InternalApiKey.DEVELOPMENT_API_KEY + "x").getStatusCode().value());

        HttpHeaders bearer = jsonHeaders();
        bearer.setBearerAuth(TestTokens.admin(UUID.randomUUID()));
        assertEquals(401, rest.postForEntity(EVENTS, new HttpEntity<>(body, bearer), String.class)
                .getStatusCode().value(), "a JWT is never enough for the internal route");

        assertEquals(0, count("SELECT COUNT(*) FROM seat_inventories"), "nothing may be processed");
        assertEquals(202, post(body, InternalApiKey.DEVELOPMENT_API_KEY).getStatusCode().value());
    }

    // ------------------------------------------------------------------- validation (400)

    @Test
    void unknownTypesAndMissingFieldsAreA400() throws Exception {
        assertBadRequest(event("NotAnEvent", Map.of()), "Unsupported event type: NotAnEvent");
        // dev-only types are not accepted on the internal route
        assertBadRequest(event("PaymentAuthorized", Map.of("bookingId", UUID.randomUUID())),
                "Unsupported event type: PaymentAuthorized");
        assertBadRequest(event("GroupCompleted", Map.of()), "groupId is required for GroupCompleted");
        assertBadRequest(event("TripGroupOpened", Map.of("groupId", UUID.randomUUID())),
                "capacity (>= 1) is required for TripGroupOpened");
        assertBadRequest(event("ProposalAccepted", Map.of("groupId", UUID.randomUUID())),
                "driverId is required for ProposalAccepted");
        assertBadRequest(event("GroupRejected", Map.of("groupId", UUID.randomUUID())),
                "driverId is required for GroupRejected");
        assertBadRequest(event("TripStarted", Map.of("groupId", UUID.randomUUID())),
                "tripId is required for TripStarted");
        assertBadRequest(event("BoardingValidated", Map.of("bookingId", UUID.randomUUID())),
                "tripId is required for BoardingValidated");
        assertBadRequest(event("TripCompleted", Map.of("groupId", UUID.randomUUID(), "tripId", UUID.randomUUID(),
                "driverId", UUID.randomUUID(), "completedAt", Instant.now().toString())),
                "boardedBookingIds is required for TripCompleted");
        assertBadRequest(event("TripCancelled", Map.of("groupId", UUID.randomUUID())),
                "tripId is required for TripCancelled");
        assertBadRequest(event("GroupCompleted", Map.of("groupId", "not-a-uuid")),
                "groupId must be a UUID in GroupCompleted");

        // the envelope is mandatory too
        assertBadRequest(objectMapper.writeValueAsString(Map.of("type", "GroupCompleted",
                "occurredAt", Instant.now().toString(), "groupId", UUID.randomUUID().toString())),
                "eventId is required for GroupCompleted");
        assertBadRequest(objectMapper.writeValueAsString(Map.of("type", "GroupCompleted",
                "eventId", UUID.randomUUID().toString(), "groupId", UUID.randomUUID().toString())),
                "occurredAt is required for GroupCompleted");
        assertBadRequest("{\"eventId\":\"" + UUID.randomUUID() + "\"}", "type is required");
        assertEquals(0, count("SELECT COUNT(*) FROM processed_events"), "a rejected event is not recorded");
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void aRepeatedEventIdIsAcknowledgedWithoutProcessingItAgain() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String body = event(eventId, "TripGroupOpened", Map.of("groupId", groupId, "capacity", 4));

        ResponseEntity<String> first = postInternal(body);
        assertEquals(202, first.getStatusCode().value(), first.getBody());
        assertEquals("PROCESSED", json(first).get("status").asText());
        assertEquals(eventId.toString(), json(first).get("eventId").asText());

        // the seat inventory is removed: a re-processing would create it again
        jdbcTemplate.update("DELETE FROM seat_inventories WHERE group_id = ?", groupId);
        ResponseEntity<String> again = postInternal(body);
        assertEquals(202, again.getStatusCode().value(), again.getBody());
        assertEquals("DUPLICATE", json(again).get("status").asText());
        assertEquals(0, count("SELECT COUNT(*) FROM seat_inventories"), "the event must not run twice");
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = '" + eventId + "'"));
    }

    // ------------------------------------------------------------ Trip Planning events

    @Test
    void tripGroupOpenedCreatesTheSeatInventoryOnce() {
        UUID groupId = UUID.randomUUID();
        assertAccepted(event("TripGroupOpened", Map.of("groupId", groupId, "capacity", 3)));
        assertAccepted(event("TripGroupOpened", Map.of("groupId", groupId, "capacity", 9)));

        Map<String, Object> inventory = jdbcTemplate.queryForMap(
                "SELECT capacity, available_seats FROM seat_inventories WHERE group_id = ?", groupId);
        assertEquals(3, inventory.get("capacity"), "a second TripGroupOpened changes nothing");
        assertEquals(3, inventory.get("available_seats"));
    }

    @Test
    void groupCompletedOnlyGroupsBookingsWithAnAuthorizedPayment() {
        UUID groupId = openGroup(4);
        UUID paid = book(groupId, UUID.randomUUID());
        UUID unpaid = book(groupId, UUID.randomUUID());
        authorize(paid);

        assertAccepted(event("GroupCompleted", Map.of("groupId", groupId)));

        assertEquals("grouped", bookingStatus(paid));
        assertEquals("SEARCH_DRIVER", sagaStep(paid));
        assertEquals("requested", bookingStatus(unpaid), "a booking without authorized payment is not grouped");
        assertEquals("AUTHORIZE_PAYMENT", sagaStep(unpaid));
    }

    @Test
    void proposalAcceptedConfirmsTheGroupAndQueuesOneGroupConfirmedMessage() throws Exception {
        UUID groupId = openGroup(4);
        UUID first = groupedBooking(groupId);
        UUID second = groupedBooking(groupId);
        UUID driverId = UUID.randomUUID();

        assertAccepted(event("ProposalAccepted", Map.of("groupId", groupId, "driverId", driverId)));

        assertEquals("confirmed", bookingStatus(first));
        assertEquals("confirmed", bookingStatus(second));
        List<JsonNode> messages = outbox("GROUP_CONFIRMED");
        assertEquals(1, messages.size(), "ONE message per accepted group");
        JsonNode message = messages.get(0);
        assertEquals(groupId.toString(), message.get("grupoId").asText());
        assertEquals(driverId.toString(), message.get("conductorId").asText());
        assertEquals(2, message.get("pasajeros").size());

        // a re-delivery (new eventId) confirms nothing and queues nothing
        assertAccepted(event("ProposalAccepted", Map.of("groupId", groupId, "driverId", driverId)));
        assertEquals(1, outbox("GROUP_CONFIRMED").size());
    }

    @Test
    void groupRejectedCancelsTheActiveBookingsWithoutPenaltyAndReleasesTheSeats() throws Exception {
        UUID groupId = openGroup(4);
        UUID paid = groupedBooking(groupId);
        UUID unpaid = book(groupId, UUID.randomUUID());
        assertEquals(2, availableSeats(groupId));

        assertAccepted(event("GroupRejected", Map.of("groupId", groupId, "driverId", UUID.randomUUID())));

        for (UUID bookingId : List.of(paid, unpaid)) {
            assertEquals("cancelled", bookingStatus(bookingId));
            assertEquals("NO_DRIVER_AVAILABLE", cancellationReason(bookingId));
            assertEquals("compensated", sagaStatus(bookingId));
        }
        assertEquals(4, availableSeats(groupId), "every seat must come back");
        assertEquals(0, count("SELECT COUNT(*) FROM penalties"), "no penalty for a rejected group");

        // only the booking with an authorized payment is reported to Payments
        List<JsonNode> cancelled = outbox("BOOKING_CANCELLED");
        assertEquals(1, cancelled.size());
        assertEquals(paid.toString(), cancelled.get(0).get("bookingId").asText());
        assertEquals("NO_DRIVER_AVAILABLE", cancelled.get(0).get("reason").asText());
        assertTrue(cancelled.get(0).hasNonNull("scheduledDepartureAt"));

        // a group without bookings is still a 202
        assertAccepted(event("GroupRejected", Map.of("groupId", UUID.randomUUID(), "driverId", UUID.randomUUID())));
    }

    // ----------------------------------------------------------- Trip Execution events

    @Test
    void boardingTripStartedAndTripCompletedCloseTheBookingsAndFlagTheNoShows() throws Exception {
        UUID groupId = openGroup(4);
        UUID boarded = groupedBooking(groupId);
        UUID noShow = groupedBooking(groupId);
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        assertAccepted(event("ProposalAccepted", Map.of("groupId", groupId, "driverId", driverId)));

        assertAccepted(event("BoardingValidated", Map.of("bookingId", boarded, "tripId", tripId)));
        assertEquals("boarded", bookingStatus(boarded));
        assertAccepted(event("TripStarted", Map.of("groupId", groupId, "tripId", tripId)));
        assertEquals("in_progress", bookingStatus(boarded));
        assertEquals("confirmed", bookingStatus(noShow), "a passenger who did not board stays confirmed");

        // distanceKm null: GroupInfoProvider supplies the 12 km of the fake provider
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("groupId", groupId);
        fields.put("tripId", tripId);
        fields.put("driverId", driverId);
        fields.put("completedAt", "2026-11-27T15:30:00Z");
        fields.put("distanceKm", null);
        fields.put("boardedBookingIds", List.of(boarded.toString()));
        assertAccepted(event("TripCompleted", fields));

        assertEquals("completed", bookingStatus(boarded));
        assertEquals(0, new BigDecimal("12").compareTo(jdbcTemplate.queryForObject(
                "SELECT distance_km FROM fares WHERE booking_id = ? AND fare_type = 'final'",
                BigDecimal.class, boarded)), "the distance falls back to GroupInfoProvider");
        assertEquals("cancelled", bookingStatus(noShow));
        assertEquals("NO_SHOW", cancellationReason(noShow));

        List<JsonNode> charges = outbox("TRIP_COMPLETED");
        assertEquals(1, charges.size());
        assertEquals(tripId.toString(), charges.get(0).get("tripId").asText());
        assertEquals(driverId.toString(), charges.get(0).get("driverId").asText());
        assertEquals(1, charges.get(0).get("bookings").size(), "only the completed booking is charged");
        List<JsonNode> noShows = outbox("BOOKING_CANCELLED");
        assertEquals(1, noShows.size());
        assertEquals("NO_SHOW", noShows.get(0).get("reason").asText());
    }

    @Test
    void tripCancelledCancelsTheConfirmedBookings() throws Exception {
        UUID groupId = openGroup(4);
        UUID bookingId = groupedBooking(groupId);
        assertAccepted(event("ProposalAccepted", Map.of("groupId", groupId, "driverId", UUID.randomUUID())));

        assertAccepted(event("TripCancelled", Map.of("groupId", groupId, "tripId", UUID.randomUUID(),
                "reason", "Conductor no disponible")));

        assertEquals("cancelled", bookingStatus(bookingId));
        assertEquals("NO_DRIVER_AVAILABLE", cancellationReason(bookingId));
        assertEquals(4, availableSeats(groupId));
        assertEquals(1, outbox("BOOKING_CANCELLED").size());
    }

    /** Valid events that do not match anything are acknowledged (and logged), never 4xx. */
    @Test
    void validEventsThatChangeNothingAreStillAccepted() {
        UUID groupId = openGroup(4);
        UUID requested = book(groupId, UUID.randomUUID());

        assertAccepted(event("BoardingValidated", Map.of("bookingId", UUID.randomUUID(), "tripId", UUID.randomUUID())));
        assertAccepted(event("BoardingValidated", Map.of("bookingId", requested, "tripId", UUID.randomUUID())));
        assertAccepted(event("GroupCompleted", Map.of("groupId", UUID.randomUUID())));
        assertAccepted(event("ProposalAccepted", Map.of("groupId", UUID.randomUUID(), "driverId", UUID.randomUUID())));
        assertAccepted(event("TripStarted", Map.of("groupId", UUID.randomUUID(), "tripId", UUID.randomUUID())));
        assertAccepted(event("TripCompleted", Map.of("groupId", groupId, "tripId", UUID.randomUUID(),
                "driverId", UUID.randomUUID(), "completedAt", Instant.now().toString(), "distanceKm", 10,
                "boardedBookingIds", List.of())));
        assertAccepted(event("TripCancelled", Map.of("groupId", UUID.randomUUID(), "tripId", UUID.randomUUID(),
                "reason", "x")));

        assertEquals("requested", bookingStatus(requested), "nothing may move");
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_messages WHERE type <> 'PAYMENT_AUTHORIZATION'"));
    }

    // --------------------------------------------------------------------------- helpers

    private UUID openGroup(int capacity) {
        UUID groupId = UUID.randomUUID();
        assertAccepted(event("TripGroupOpened", Map.of("groupId", groupId, "capacity", capacity)));
        return groupId;
    }

    private UUID book(UUID groupId, UUID passengerId) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(TestTokens.passenger(passengerId));
        ResponseEntity<String> response = rest.postForEntity("/api/v1/bookings", new HttpEntity<>(
                Map.of("groupId", groupId.toString(), "requestId", UUID.randomUUID().toString()), headers),
                String.class);
        assertEquals(202, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(json(response).get("bookingId").asText());
    }

    private void authorize(UUID bookingId) {
        eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), Instant.now(), bookingId, UUID.randomUUID()));
    }

    /** A booking whose payment was authorized and whose group completed. */
    private UUID groupedBooking(UUID groupId) {
        UUID bookingId = book(groupId, UUID.randomUUID());
        authorize(bookingId);
        assertAccepted(event("GroupCompleted", Map.of("groupId", groupId)));
        assertEquals("grouped", bookingStatus(bookingId));
        return bookingId;
    }

    private String event(String type, Map<String, ?> fields) {
        return event(UUID.randomUUID(), type, fields);
    }

    private String event(UUID eventId, String type, Map<String, ?> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("occurredAt", Instant.now().toString());
        body.put("type", type);
        fields.forEach((name, value) -> body.put(name, value instanceof UUID uuid ? uuid.toString() : value));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertAccepted(String body) {
        ResponseEntity<String> response = postInternal(body);
        assertEquals(202, response.getStatusCode().value(), response.getBody());
    }

    private void assertBadRequest(String body, String message) {
        ResponseEntity<String> response = postInternal(body);
        assertEquals(400, response.getStatusCode().value(), response.getBody());
        assertTrue(json(response).get("message").asText().startsWith(message),
                "expected '" + message + "' but got: " + response.getBody());
    }

    private ResponseEntity<String> postInternal(String body) {
        return post(body, InternalApiKey.DEVELOPMENT_API_KEY);
    }

    private ResponseEntity<String> post(String body, String apiKey) {
        HttpHeaders headers = jsonHeaders();
        if (apiKey != null) headers.set("X-Internal-Api-Key", apiKey);
        return rest.postForEntity(EVENTS, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new AssertionError("Body is not JSON: " + response.getBody(), exception);
        }
    }

    private List<JsonNode> outbox(String type) {
        return jdbcTemplate.queryForList(
                        "SELECT payload FROM outbox_messages WHERE type = ? ORDER BY sequence_number", String.class, type)
                .stream().map(payload -> {
                    try {
                        return objectMapper.readTree(payload);
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                }).toList();
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? -1 : count;
    }

    private String bookingStatus(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId);
    }

    private String cancellationReason(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT cancellation_reason FROM bookings WHERE booking_id = ?",
                String.class, bookingId);
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
