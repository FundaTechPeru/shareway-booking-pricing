package com.fundatechperu.shareway.booking.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundatechperu.shareway.booking.application.service.InternalEventService;
import com.fundatechperu.shareway.booking.infrastructure.OpenApiConfiguration;
import com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse;
import com.fundatechperu.shareway.booking.infrastructure.web.EventProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/**
 * Entry point of the integration events of Trip Planning and Trip Execution. Only callable
 * with the X-Internal-Api-Key header (401 otherwise). The event is dispatched through the
 * EventPublisher to the same handlers the rest of the saga uses.
 * - 202: processed, or an eventId already processed (not processed again), or a valid event
 *   that changes nothing (logged): the senders treat every 4xx as a definitive failure.
 * - 400: unknown type or a missing/malformed field.
 * - 503: the event could not be processed now (nothing was committed); the sender retries.
 */
@RestController
@RequestMapping("/api/v1/internal/events")
@Tag(name = "Internal events", description = "Service-to-service integration events (X-Internal-Api-Key only).")
@SecurityRequirement(name = OpenApiConfiguration.INTERNAL_API_KEY_SCHEME)
public class InternalEventController {

    private static final Logger log = LoggerFactory.getLogger(InternalEventController.class);

    private final InternalEventService internalEventService;
    private final IntegrationEventParser parser;

    public InternalEventController(InternalEventService internalEventService, Clock clock) {
        this.internalEventService = internalEventService;
        this.parser = new IntegrationEventParser(IntegrationEventParser.Mode.INTERNAL, clock);
    }

    @Operation(summary = "Receive an integration event",
            description = "Body: {\"eventId\", \"occurredAt\", \"type\", ...fields}. Types: TripGroupOpened "
                    + "{groupId, capacity}, GroupCompleted {groupId}, ProposalAccepted {groupId, driverId}, "
                    + "GroupRejected {groupId, driverId} (Trip Planning); TripStarted {groupId, tripId}, "
                    + "BoardingValidated {bookingId, tripId}, TripCompleted {groupId, tripId, driverId, completedAt, "
                    + "distanceKm (nullable), boardedBookingIds}, TripCancelled {groupId, tripId, reason} "
                    + "(Trip Execution). Idempotent by eventId.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Processed, already processed, or nothing to change",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InternalEventResponse.class),
                            examples = @ExampleObject(value = "{\"eventId\":\"6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f\",\"type\":\"GroupCompleted\",\"status\":\"PROCESSED\"}"))),
            @ApiResponse(responseCode = "400", description = "Unknown type or a required field is missing",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"groupId is required for GroupCompleted\",\"path\":\"/api/v1/internal/events\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Internal-Api-Key",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid internal API key\",\"path\":\"/api/v1/internal/events\"}"))),
            @ApiResponse(responseCode = "503", description = "Temporarily unable to process the event; send it again",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":503,\"error\":\"Service Unavailable\",\"message\":\"The event could not be processed now; send it again\",\"path\":\"/api/v1/internal/events\"}")))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InternalEventResponse> receive(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {"eventId":"6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f",
                                     "occurredAt":"2026-11-27T15:00:00Z",
                                     "type":"TripGroupOpened",
                                     "groupId":"7a2b3c4d-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
                                     "capacity":4}""")))
            @RequestBody JsonNode body) {
        IntegrationEventParser.ParsedEvent parsed = parser.parse(body);
        boolean processed;
        try {
            processed = internalEventService.process(parsed.eventId(), parsed.type(), parsed.event());
        } catch (RuntimeException exception) {
            log.error("Integration event {} ({}) could not be processed; nothing was committed.",
                    parsed.eventId(), parsed.type(), exception);
            throw new EventProcessingException(exception);
        }
        return ResponseEntity.accepted().body(new InternalEventResponse(parsed.eventId(), parsed.type(),
                processed ? "PROCESSED" : "DUPLICATE"));
    }

    /** @param status PROCESSED, or DUPLICATE when the eventId had already been processed */
    public record InternalEventResponse(UUID eventId, String type, String status) {
    }
}
