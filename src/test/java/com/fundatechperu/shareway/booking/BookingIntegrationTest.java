package com.fundatechperu.shareway.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
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

import java.time.Instant;
import java.util.ArrayList;
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
 * End-to-end behaviour against local PostgreSQL (no Testcontainers): the real HTTP server,
 * the gateway-trusted X-User-Id header (no JWT inside the service), one local transaction per
 * request and the in-memory event bus. The schema is reset per class by
 * {@link AbstractPostgresIntegrationTest} (Flyway clean + migrate, V2 seed included) and the
 * booking tables per test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NO_SEATS_MESSAGE = "El viaje ya no tiene asientos disponibles.";
    private static final UUID PASSENGER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublisher eventPublisher;

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
    }

    /**
     * (1) Last seat: 20 simultaneous POST /bookings with distinct Idempotency-Key over a group
     * with available_seats = 1. Exactly one 202 and nineteen 409 with the mandated message;
     * available_seats ends in 0 with exactly one booking and one saga.
     */
    @Test
    void lastSeatIsTakenByExactlyOneOfTwentySimultaneousRequests() throws Exception {
        UUID groupId = insertSeatInventory(1, 1);
        int attempts = 20;

        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>(attempts);
            for (int i = 0; i < attempts; i++) {
                String requestId = UUID.randomUUID().toString();
                String idempotencyKey = UUID.randomUUID().toString();
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return postBooking(groupId, requestId, idempotencyKey);
                }));
            }
            startLine.countDown();

            int accepted = 0;
            int conflicts = 0;
            ResponseEntity<String> acceptedResponse = null;
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(2, TimeUnit.MINUTES);
                int status = response.getStatusCode().value();
                if (status == 202) {
                    accepted++;
                    acceptedResponse = response;
                } else if (status == 409) {
                    conflicts++;
                    assertEquals(NO_SEATS_MESSAGE, messageOf(response),
                            "every 409 must carry the mandated message");
                } else {
                    fail("Unexpected status " + status + " with body: " + response.getBody());
                }
            }

            assertEquals(1, accepted, "exactly one request must win the last seat");
            assertEquals(19, conflicts);

            JsonNode body = objectMapper.readTree(acceptedResponse.getBody());
            assertEquals("REQUESTED", body.get("status").asText());
            assertNotNull(body.get("bookingId").asText());

            assertEquals(0, availableSeats(groupId), "available_seats must end in 0");
            assertEquals(1, bookingCount(groupId), "exactly one booking must be created");
            assertEquals(1, sagaCount(groupId), "exactly one saga must be created");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * (2) Retry with the same Idempotency-Key: the original booking is returned and the seat
     * is held only once.
     */
    @Test
    void sameIdempotencyKeyReplaysTheBookingAndHoldsTheSeatOnlyOnce() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        String requestId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<String> first = postBooking(groupId, requestId, idempotencyKey);
        assertEquals(202, first.getStatusCode().value(), String.valueOf(first.getBody()));
        String bookingId = objectMapper.readTree(first.getBody()).get("bookingId").asText();
        assertEquals(3, availableSeats(groupId), "the first request holds one seat");

        ResponseEntity<String> replay = postBooking(groupId, requestId, idempotencyKey);
        assertEquals(202, replay.getStatusCode().value(), String.valueOf(replay.getBody()));
        JsonNode replayBody = objectMapper.readTree(replay.getBody());
        assertEquals(bookingId, replayBody.get("bookingId").asText(), "the same booking must come back");
        assertEquals("REQUESTED", replayBody.get("status").asText());

        assertEquals(3, availableSeats(groupId), "the replay must not hold a second seat");
        assertEquals(1, bookingCount(groupId), "no second booking may exist");
        assertEquals(1, sagaCount(groupId), "no second saga may exist");
    }

    /**
     * (3) Rejected payment: after PaymentAuthorizationFailed the booking is cancelled, the
     * saga is compensated and available_seats returns to its original value.
     */
    @Test
    void rejectedPaymentCompensatesBookingSeatAndSaga() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);

        ResponseEntity<String> created = postBooking(groupId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        assertEquals(202, created.getStatusCode().value(), String.valueOf(created.getBody()));
        UUID bookingId = UUID.fromString(objectMapper.readTree(created.getBody()).get("bookingId").asText());
        assertEquals(3, availableSeats(groupId), "creating the booking holds one seat");

        Map<String, Object> sagaBefore = jdbcTemplate.queryForMap(
                "SELECT status, current_step, deadline_at FROM booking_sagas WHERE booking_id = ?", bookingId);
        assertEquals("running", sagaBefore.get("status"));
        assertEquals("AUTHORIZE_PAYMENT", sagaBefore.get("current_step"));
        assertNotNull(sagaBefore.get("deadline_at"));

        // same in-memory bus and handler used in production
        eventPublisher.publish(new PaymentAuthorizationFailed(UUID.randomUUID(), Instant.now(),
                bookingId, "card declined"));

        ResponseEntity<String> after = getBooking(bookingId);
        assertEquals(200, after.getStatusCode().value(), String.valueOf(after.getBody()));
        assertEquals("CANCELLED", objectMapper.readTree(after.getBody()).get("status").asText());

        assertEquals("cancelled", jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId));
        assertEquals("compensated", jdbcTemplate.queryForObject(
                "SELECT status FROM booking_sagas WHERE booking_id = ?", String.class, bookingId));
        assertEquals(4, availableSeats(groupId), "available_seats must return to its original value");
    }

    /**
     * Cancellation runs only on the X-User-Id header delivered by the API Gateway: the owner
     * cancels and the seat comes back, another passenger gets 404 and a missing header 400.
     * No Authorization header is ever needed or sent.
     */
    @Test
    void cancelTrustsOnlyTheXUserDeliveredByTheGateway() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        ResponseEntity<String> created = postBooking(groupId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        assertEquals(202, created.getStatusCode().value(), String.valueOf(created.getBody()));
        UUID bookingId = UUID.fromString(objectMapper.readTree(created.getBody()).get("bookingId").asText());
        assertEquals(3, availableSeats(groupId));

        // missing X-User-Id -> 400
        ResponseEntity<String> noHeader = cancel(bookingId, null);
        assertEquals(400, noHeader.getStatusCode().value());
        assertEquals("X-User-Id header is required", messageOf(noHeader));

        // another passenger -> 404 (the booking does not exist for them)
        ResponseEntity<String> stranger = cancel(bookingId, UUID.randomUUID());
        assertEquals(404, stranger.getStatusCode().value());
        assertEquals(3, availableSeats(groupId), "the seat must still be held");

        // the owner cancels -> 200 and the seat is released (Sprint 2 body: flat, with penalty fields)
        ResponseEntity<String> owner = cancel(bookingId, PASSENGER_ID);
        assertEquals(200, owner.getStatusCode().value(), String.valueOf(owner.getBody()));
        JsonNode cancelBody = objectMapper.readTree(owner.getBody());
        assertEquals(bookingId.toString(), cancelBody.get("bookingId").asText());
        assertEquals("CANCELLED", cancelBody.get("status").asText());
        assertTrue(cancelBody.get("penaltyAmount").isNull(),
                "a cancellation far from the departure must not be fined");
        assertEquals(4, availableSeats(groupId), "available_seats must go back to 4");
        assertEquals("cancelled", jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId));
    }

    /**
     * Contract of the error codes: client mistakes are 400, unknown resources (including
     * unknown URL paths) are 404 and an existing path reached with the wrong HTTP method is
     * 405. None of these may fall into the generic 500 handler.
     */
    @Test
    void errorStatusCodesFollowTheContract() throws Exception {
        // 400: missing groupId, malformed UUIDs and missing X-User-Id
        assertEquals(400, statusOf(HttpMethod.GET, "/fares/estimate", null));
        assertEquals(400, statusOf(HttpMethod.GET, "/fares/estimate?groupId=not-a-uuid", null));
        assertEquals(400, statusOf(HttpMethod.GET, "/bookings/not-a-uuid", null));

        HttpHeaders headers = jsonHeaders();
        ResponseEntity<String> noPassenger = rest.exchange("/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("groupId", UUID.randomUUID().toString(),
                        "requestId", UUID.randomUUID().toString()), headers), String.class);
        assertEquals(400, noPassenger.getStatusCode().value());
        assertEquals("X-User-Id header is required", messageOf(noPassenger));

        // 404: unknown booking, unknown group and unknown URL path
        assertEquals(404, statusOf(HttpMethod.GET, "/bookings/" + UUID.randomUUID(), null));
        assertEquals(404, statusOf(HttpMethod.GET,
                "/fares/estimate?groupId=" + UUID.randomUUID(), null));
        assertEquals(404, statusOf(HttpMethod.GET, "/definitely-not-here", null));

        // 405: GET collection of bookings and POST over a GET-only path
        assertEquals(405, statusOf(HttpMethod.GET, "/bookings", null));
        assertEquals(405, statusOf(HttpMethod.POST, "/fares/estimate", "{}"));
    }

    /**
     * Actuator contract: only health and info are exposed, and the health
     * endpoint answers with liveness and readiness probes. Every other actuator endpoint
     * must not exist.
     */
    @Test
    void actuatorExposesOnlyHealthAndInfoWithProbes() throws Exception {
        JsonNode links = objectMapper.readTree(
                rest.exchange("/actuator", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class).getBody())
                .path("_links");
        assertTrue(links.has("health"), "health must be exposed");
        assertTrue(links.has("info"), "info must be exposed");
        assertFalse(links.has("env"), "env must not be exposed");
        assertFalse(links.has("beans"), "beans must not be exposed");
        assertFalse(links.has("configprops"), "configprops must not be exposed");
        assertFalse(links.has("loggers"), "loggers must not be exposed");

        assertEquals(200, statusOf(HttpMethod.GET, "/actuator/health", null));
        assertEquals(200, statusOf(HttpMethod.GET, "/actuator/health/liveness", null));
        assertEquals(200, statusOf(HttpMethod.GET, "/actuator/health/readiness", null));
        assertEquals(200, statusOf(HttpMethod.GET, "/actuator/info", null));

        // no details are leaked (show-details: never)
        JsonNode health = objectMapper.readTree(
                rest.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class)
                        .getBody());
        assertEquals("UP", health.path("status").asText());
        assertTrue(health.path("components").isMissingNode(), "components must stay hidden");

        assertEquals(404, statusOf(HttpMethod.GET, "/actuator/env", null));
        assertEquals(404, statusOf(HttpMethod.GET, "/actuator/loggers", null));
        assertEquals(404, statusOf(HttpMethod.GET, "/actuator/metrics", null));
    }

    /**
     * Swagger/OpenAPI contract: the UI lives at /swagger-ui.html and the machine
     * document at /v3/api-docs. The document must describe the endpoints and the X-User-Id header
     * of the API Gateway, and must not advertise any JWT security scheme (there is none left).
     */
    @Test
    void swaggerUiAndOpenApiDocumentAreExposed() throws Exception {
        ResponseEntity<String> openApi = rest.exchange("/v3/api-docs", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        assertEquals(200, openApi.getStatusCode().value(), String.valueOf(openApi.getBody()));

        JsonNode document = objectMapper.readTree(openApi.getBody());
        assertTrue(document.path("paths").has("/bookings"), "bookings must be documented");
        assertTrue(document.path("paths").has("/fares/estimate"),
                "fare estimate must be documented");
        assertTrue(openApi.getBody().contains("X-User-Id"), "the gateway header must be documented");
        assertFalse(openApi.getBody().contains("bearerAuth"), "no JWT scheme may be exposed");
        assertFalse(document.path("components").path("securitySchemes").has("bearerAuth"),
                "components must not carry bearerAuth");

        // Contract of the report: no route keeps the /api/v1 or /pricing prefix.
        List<String> paths = new ArrayList<>();
        document.path("paths").fieldNames().forEachRemaining(paths::add);
        assertTrue(paths.stream().noneMatch(path -> path.startsWith("/api/v1") || path.startsWith("/pricing")),
                "routes must not keep the old prefix: " + paths);

        // Fare rules are read-only: only GET survives, POST and PUT were removed.
        JsonNode fareRules = document.path("paths").path("/fare-rules");
        assertTrue(fareRules.has("get"), "GET /fare-rules must exist");
        assertFalse(fareRules.has("post"), "POST /fare-rules must not exist");
        assertFalse(fareRules.has("put"), "PUT /fare-rules must not exist");

        // Swagger groups keep human names: the generated fare-rule-controller must be renamed.
        List<String> tags = new ArrayList<>();
        document.path("tags").forEach(tag -> tags.add(tag.path("name").asText()));
        assertTrue(tags.contains("Fare Rules"), "the fare-rule group must be renamed: " + tags);
        assertFalse(tags.contains("fare-rule-controller"),
                "the generated group name must be gone: " + tags);

        // The UI itself: 200 directly, or 302 to /swagger-ui/index.html when redirects are not followed.
        ResponseEntity<String> ui = rest.exchange("/swagger-ui.html", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        int uiStatus = ui.getStatusCode().value();
        assertTrue(uiStatus == 200 || uiStatus == 302,
                "swagger-ui.html must answer 200 or redirect, got " + uiStatus + ": " + ui.getBody());
        if (uiStatus == 200) {
            assertTrue(ui.getBody().contains("swagger"), "the UI page must be the Swagger one");
        }

        // The UI loads its configuration from this endpoint (springdoc 2.x).
        ResponseEntity<String> config = rest.exchange("/v3/api-docs/swagger-config", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        assertEquals(200, config.getStatusCode().value(), String.valueOf(config.getBody()));
        assertTrue(config.getBody().contains("/v3/api-docs"), "the config must point at the document");
    }

    // --------------------------------------------------------------------------- helpers

    private int statusOf(HttpMethod method, String path, String body) {
        ResponseEntity<String> response = body == null
                ? rest.exchange(path, method, new HttpEntity<>(jsonHeaders()), String.class)
                : rest.exchange(path, method, new HttpEntity<>(body, jsonHeaders()), String.class);
        return response.getStatusCode().value();
    }

    private ResponseEntity<String> postBooking(UUID groupId, String requestId, String idempotencyKey) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-User-Id", PASSENGER_ID.toString());
        headers.set("Idempotency-Key", idempotencyKey);
        Map<String, String> payload = Map.of("groupId", groupId.toString(), "requestId", requestId);
        return rest.postForEntity("/bookings", new HttpEntity<>(payload, headers), String.class);
    }

    private ResponseEntity<String> getBooking(UUID bookingId) {
        return rest.exchange("/bookings/{bookingId}", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class, bookingId);
    }

    private ResponseEntity<String> cancel(UUID bookingId, UUID actorId) {
        HttpHeaders headers = jsonHeaders();
        if (actorId != null) headers.set("X-User-Id", actorId.toString());
        return rest.exchange("/bookings/{bookingId}/cancel", HttpMethod.POST,
                new HttpEntity<>(headers), String.class, bookingId);
    }

    /** JSON headers without Authorization: the service reads only X-User-Id. */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String messageOf(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody()).get("message").asText();
        } catch (Exception exception) {
            throw new AssertionError("Body is not a JSON error: " + response.getBody(), exception);
        }
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

    private int bookingCount(UUID groupId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE group_id = ?", Integer.class, groupId);
        return count == null ? -1 : count;
    }

    private int sagaCount(UUID groupId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_sagas WHERE booking_id IN "
                        + "(SELECT booking_id FROM bookings WHERE group_id = ?)", Integer.class, groupId);
        return count == null ? -1 : count;
    }
}
