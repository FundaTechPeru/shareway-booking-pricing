package com.fundatechperu.shareway.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.support.TestTokens;
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
 * the Bearer JWT validated by the service (401 without a valid token, 403 for a wrong role,
 * 404 for someone else's booking), one local transaction per request and the in-memory event
 * bus. The schema is reset per class by {@link AbstractPostgresIntegrationTest} (Flyway clean
 * + migrate, V3 fare-rule baseline included) and the booking tables per test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NO_SEATS_MESSAGE = "The trip has no seats available.";
    private static final UUID PASSENGER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventPublisher eventPublisher;

    @BeforeEach
    void isolateBookingData() {
        // fare_rules is kept: it carries the V3 baseline (rule version 1) for the whole class.
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
     * (1) Last seat: 20 simultaneous POST /api/v1/bookings with distinct Idempotency-Key over a group
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
     * Cancellation runs only for the subject of the JWT: the owner cancels and the seat comes
     * back, another passenger gets 404 and a request without a token 401.
     */
    @Test
    void cancelIsOnlyAllowedToTheOwnerOfTheToken() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        ResponseEntity<String> created = postBooking(groupId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        assertEquals(202, created.getStatusCode().value(), String.valueOf(created.getBody()));
        UUID bookingId = UUID.fromString(objectMapper.readTree(created.getBody()).get("bookingId").asText());
        assertEquals(3, availableSeats(groupId));

        // no token -> 401
        ResponseEntity<String> anonymous = rest.exchange("/api/v1/bookings/{bookingId}/cancel", HttpMethod.POST,
                new HttpEntity<>(jsonHeaders()), String.class, bookingId);
        assertEquals(401, anonymous.getStatusCode().value());

        // another passenger -> 404 (the booking does not exist for them)
        ResponseEntity<String> stranger = cancel(bookingId, UUID.randomUUID());
        assertEquals(404, stranger.getStatusCode().value());
        assertEquals(3, availableSeats(groupId), "the seat must still be held");

        // the owner cancels -> 200 and the seat is released
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
        assertEquals("PASSENGER_CANCELLED", jdbcTemplate.queryForObject(
                "SELECT cancellation_reason FROM bookings WHERE booking_id = ?", String.class, bookingId));
    }

    /**
     * JWT contract: a missing, expired or foreign-signed token is 401 on every protected route;
     * POST /bookings needs the PASSENGER role (403 otherwise); someone else's booking is 404
     * for a passenger and visible (without boarding codes) for an admin; the list shows a
     * passenger only their bookings and an admin all of them. Health, Swagger and the OpenAPI
     * document stay public and the internal route never accepts a JWT.
     */
    @Test
    void theJwtDecidesWhoMayDoWhat() throws Exception {
        UUID groupId = insertSeatInventory(4, 4);
        ResponseEntity<String> created = postBooking(groupId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        assertEquals(202, created.getStatusCode().value(), String.valueOf(created.getBody()));
        String bookingId = objectMapper.readTree(created.getBody()).get("bookingId").asText();

        // 401: no token, expired token, token signed with another secret, malformed token
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId, null, null));
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId,
                TestTokens.expired(PASSENGER_ID, "PASSENGER"), null));
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId,
                TestTokens.signedWithAnotherSecret(PASSENGER_ID, "PASSENGER"), null));
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/bookings", "not-a-jwt", null));
        assertEquals(401, exchange(HttpMethod.GET, "/api/v1/fare-rules", null, null));
        ResponseEntity<String> unauthorized = rest.exchange("/api/v1/bookings", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        assertEquals("Missing, invalid or expired access token", messageOf(unauthorized));

        // 403: only a PASSENGER books; a DRIVER or a token without role may not
        String body = objectMapper.writeValueAsString(Map.of("groupId", groupId.toString(),
                "requestId", UUID.randomUUID().toString()));
        assertEquals(403, exchange(HttpMethod.POST, "/api/v1/bookings", TestTokens.driver(UUID.randomUUID()), body));
        assertEquals(403, exchange(HttpMethod.POST, "/api/v1/bookings", TestTokens.admin(UUID.randomUUID()), body));
        assertEquals(403, exchange(HttpMethod.POST, "/api/v1/bookings",
                TestTokens.withoutRole(UUID.randomUUID()), body));
        assertEquals(403, exchange(HttpMethod.GET, "/api/v1/bookings", TestTokens.driver(UUID.randomUUID()), null));
        assertEquals(3, availableSeats(groupId), "no forbidden request may hold a seat");

        // 404: another passenger cannot see the booking; the owner and an admin can
        assertEquals(404, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId,
                TestTokens.passenger(UUID.randomUUID()), null));
        assertEquals(200, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId,
                TestTokens.passenger(PASSENGER_ID), null));
        assertEquals(200, exchange(HttpMethod.GET, "/api/v1/bookings/" + bookingId,
                TestTokens.admin(UUID.randomUUID()), null));

        // list: the passenger sees only theirs, the admin sees everything
        UUID otherPassenger = UUID.randomUUID();
        HttpHeaders otherHeaders = jsonHeaders();
        otherHeaders.setBearerAuth(TestTokens.passenger(otherPassenger));
        assertEquals(202, rest.postForEntity("/api/v1/bookings", new HttpEntity<>(body, otherHeaders), String.class)
                .getStatusCode().value());
        JsonNode mine = objectMapper.readTree(get("/api/v1/bookings", TestTokens.passenger(PASSENGER_ID)).getBody());
        assertEquals(1, mine.size());
        assertEquals(bookingId, mine.get(0).get("bookingId").asText());
        assertEquals(groupId.toString(), mine.get(0).get("groupId").asText());
        JsonNode all = objectMapper.readTree(get("/api/v1/bookings", TestTokens.admin(UUID.randomUUID())).getBody());
        assertEquals(2, all.size());

        // any authenticated user may read fares and fare rules
        assertEquals(200, exchange(HttpMethod.GET, "/api/v1/fare-rules", TestTokens.driver(UUID.randomUUID()), null));
        assertEquals(200, exchange(HttpMethod.GET, "/api/v1/fares/estimate?groupId=" + groupId,
                TestTokens.withoutRole(UUID.randomUUID()), null));

        // public routes need no token; the internal route never accepts a JWT
        assertEquals(200, exchange(HttpMethod.GET, "/actuator/health", null, null));
        assertEquals(200, exchange(HttpMethod.GET, "/v3/api-docs", null, null));
        assertEquals(401, exchange(HttpMethod.POST, "/api/v1/internal/events", TestTokens.admin(UUID.randomUUID()),
                "{\"type\":\"GroupCompleted\"}"));
    }

    /**
     * Contract of the error codes: client mistakes are 400, unknown resources (including
     * unknown URL paths) are 404 and an existing path reached with the wrong HTTP method is
     * 405. None of these may fall into the generic 500 handler.
     */
    @Test
    void errorStatusCodesFollowTheContract() throws Exception {
        // 400: missing groupId and malformed UUIDs
        assertEquals(400, statusOf(HttpMethod.GET, "/api/v1/fares/estimate", null));
        assertEquals(400, statusOf(HttpMethod.GET, "/api/v1/fares/estimate?groupId=not-a-uuid", null));
        assertEquals(400, statusOf(HttpMethod.GET, "/api/v1/bookings/not-a-uuid", null));
        assertEquals(400, statusOf(HttpMethod.POST, "/api/v1/bookings",
                "{\"requestId\":\"" + UUID.randomUUID() + "\"}"));

        // 404: unknown booking, unknown group and unknown URL path
        assertEquals(404, statusOf(HttpMethod.GET, "/api/v1/bookings/" + UUID.randomUUID(), null));
        assertEquals(404, statusOf(HttpMethod.GET,
                "/api/v1/fares/estimate?groupId=" + UUID.randomUUID(), null));
        assertEquals(404, statusOf(HttpMethod.GET, "/definitely-not-here", null));

        // 405: DELETE over the bookings collection and POST over a GET-only path
        assertEquals(405, statusOf(HttpMethod.DELETE, "/api/v1/bookings", null));
        assertEquals(405, statusOf(HttpMethod.POST, "/api/v1/fares/estimate", "{}"));
    }

    /**
     * Actuator contract: only health and info are exposed, and the health
     * endpoint answers with liveness and readiness probes. Every other actuator endpoint
     * must not exist. Only health is public.
     */
    @Test
    void actuatorExposesOnlyHealthAndInfoWithProbes() throws Exception {
        assertEquals(401, exchange(HttpMethod.GET, "/actuator", null, null), "only health is public");
        JsonNode links = objectMapper.readTree(
                rest.exchange("/actuator", HttpMethod.GET, new HttpEntity<>(authHeaders(PASSENGER_ID)), String.class)
                        .getBody())
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
     * document at /v3/api-docs. The document must describe the endpoints, the Bearer JWT scheme
     * and the internal API key scheme, and X-User-Id must be gone.
     */
    @Test
    void swaggerUiAndOpenApiDocumentAreExposed() throws Exception {
        ResponseEntity<String> openApi = rest.exchange("/v3/api-docs", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        assertEquals(200, openApi.getStatusCode().value(), String.valueOf(openApi.getBody()));

        JsonNode document = objectMapper.readTree(openApi.getBody());
        assertTrue(document.path("paths").has("/api/v1/bookings"), "bookings must be documented");
        assertTrue(document.path("paths").has("/api/v1/fares/estimate"),
                "fare estimate must be documented");
        assertTrue(document.path("paths").has("/api/v1/internal/events"), "the internal endpoint must be documented");
        assertFalse(openApi.getBody().contains("X-User-Id"), "X-User-Id is no longer read");
        assertEquals("bearer", document.path("components").path("securitySchemes").path("bearerAuth")
                .path("scheme").asText(), "the JWT scheme must be documented");
        assertEquals("X-Internal-Api-Key", document.path("components").path("securitySchemes")
                .path("internalApiKey").path("name").asText(), "the internal key must be documented");

        // Gateway contract: every business route lives under /api/v1 and none keeps the
        // old unprefixed path or a stray /pricing prefix. Actuator and springdoc stay out.
        List<String> paths = new ArrayList<>();
        document.path("paths").fieldNames().forEachRemaining(paths::add);
        assertTrue(paths.stream().allMatch(path -> path.startsWith("/api/v1")),
                "every documented route must live under /api/v1: " + paths);
        assertTrue(paths.stream().noneMatch(path -> path.startsWith("/pricing")),
                "no /pricing route may exist: " + paths);

        // Fare rules are read-only: only GET survives, POST and PUT were removed.
        JsonNode fareRules = document.path("paths").path("/api/v1/fare-rules");
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

    /**
     * Documentation quality contract (TB3): no operation may answer "Undocumented", every
     * response carries a real description, and the request bodies and success responses
     * expose an example the client can copy.
     */
    @Test
    void openApiDocumentsEveryResponseWithExamples() throws Exception {
        ResponseEntity<String> openApi = rest.exchange("/v3/api-docs", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()), String.class);
        assertEquals(200, openApi.getStatusCode().value(), String.valueOf(openApi.getBody()));
        JsonNode document = objectMapper.readTree(openApi.getBody());

        List<String> operations = List.of("get", "post", "put", "delete", "patch");
        List<String> undocumented = new ArrayList<>();
        List<String> withoutExample = new ArrayList<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields()
                .forEachRemaining(operation -> {
                    if (!operations.contains(operation.getKey())) return;
                    String where = operation.getKey().toUpperCase() + " " + path.getKey();
                    operation.getValue().path("responses").fields().forEachRemaining(response -> {
                        String description = response.getValue().path("description").asText();
                        if (description.isBlank() || "Undocumented".equalsIgnoreCase(description)) {
                            undocumented.add(where + " -> " + response.getKey());
                        }
                        // Success responses must carry a copyable example.
                        if (response.getKey().startsWith("2")) {
                            boolean hasExample = response.getValue().path("content")
                                    .path("application/json").path("examples").isObject()
                                    || response.getValue().path("content")
                                    .path("application/json").path("example").isMissingNode() == false;
                            if (!hasExample) withoutExample.add(where + " -> " + response.getKey());
                        }
                    });
                    // POST bodies must show an example too.
                    if (operation.getKey().equals("post")) {
                        boolean hasBodyExample = operation.getValue().path("requestBody").path("content")
                                .path("application/json").path("examples").isObject()
                                || !operation.getValue().path("requestBody").path("content")
                                .path("application/json").path("example").isMissingNode();
                        boolean noBody = operation.getValue().path("requestBody").isMissingNode();
                        if (!noBody && !hasBodyExample) withoutExample.add(where + " -> requestBody");
                    }
                }));

        assertTrue(undocumented.isEmpty(), "every response must be documented: " + undocumented);
        assertTrue(withoutExample.isEmpty(), "every success response and body needs an example: "
                + withoutExample + " ||| RAW: " + document.path("paths").path("/api/v1/bookings").toString());

        // The new error contract is part of the document.
        JsonNode errorSchema = document.path("components").path("schemas").path("ErrorResponse");
        for (String field : List.of("timestamp", "status", "error", "message", "path")) {
            assertTrue(errorSchema.path("properties").has(field),
                    "ErrorResponse must expose " + field + " in the OpenAPI document");
        }
    }

    // --------------------------------------------------------------------------- helpers

    /** Status of a request authenticated as the PASSENGER_ID passenger. */
    private int statusOf(HttpMethod method, String path, String body) {
        return exchange(method, path, TestTokens.passenger(PASSENGER_ID), body);
    }

    /** Status of a request with the given bearer token (none when null). */
    private int exchange(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = jsonHeaders();
        if (token != null) headers.setBearerAuth(token);
        HttpEntity<String> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        return rest.exchange(path, method, entity, String.class).getStatusCode().value();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postBooking(UUID groupId, String requestId, String idempotencyKey) {
        HttpHeaders headers = authHeaders(PASSENGER_ID);
        headers.set("Idempotency-Key", idempotencyKey);
        Map<String, String> payload = Map.of("groupId", groupId.toString(), "requestId", requestId);
        return rest.postForEntity("/api/v1/bookings", new HttpEntity<>(payload, headers), String.class);
    }

    private ResponseEntity<String> getBooking(UUID bookingId) {
        return rest.exchange("/api/v1/bookings/{bookingId}", HttpMethod.GET,
                new HttpEntity<>(authHeaders(PASSENGER_ID)), String.class, bookingId);
    }

    private ResponseEntity<String> cancel(UUID bookingId, UUID actorId) {
        HttpHeaders headers = authHeaders(actorId);
        return rest.exchange("/api/v1/bookings/{bookingId}/cancel", HttpMethod.POST,
                new HttpEntity<>(headers), String.class, bookingId);
    }

    /** JSON headers authenticated as the given passenger. */
    private HttpHeaders authHeaders(UUID passengerId) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(TestTokens.passenger(passengerId));
        return headers;
    }

    /** JSON headers without Authorization. */
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
