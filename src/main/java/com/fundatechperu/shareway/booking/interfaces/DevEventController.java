package com.fundatechperu.shareway.booking.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * Manual testing of the event bus: publishes a simulated event through the same
 * EventPublisher used by the application, converted by the same {@link IntegrationEventParser}
 * as POST /api/v1/internal/events (eventId and occurredAt are optional here, and the payment
 * and route events can also be simulated). No idempotency record is kept. Only registered with
 * the "dev" profile (--spring.profiles.active=dev); the endpoint does not exist in any other
 * profile.
 */
@RestController
@RequestMapping("/dev/events")
@Profile("dev")
@Tag(name = "Dev", description = "Simulated events for manual testing. Available only with the 'dev' profile.")
public class DevEventController {

    private final EventPublisher eventPublisher;
    private final IntegrationEventParser parser;

    public DevEventController(EventPublisher eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.parser = new IntegrationEventParser(IntegrationEventParser.Mode.DEV, clock);
    }

    @Operation(summary = "Publish a simulated event",
            description = "Dispatches the event to the same handlers that consume it in production. "
                    + "Body: {\"type\", ...fields} with the fields of POST /api/v1/internal/events. "
                    + "Supported types: TripGroupOpened, PaymentAuthorized, PaymentAuthorizationFailed, "
                    + "GroupCompleted, ProposalAccepted, GroupRejected, RouteUpdated, BoardingValidated, "
                    + "TripStarted, TripCompleted, TripCancelled.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event dispatched to the in-memory bus",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DevEventResponse.class),
                            examples = @ExampleObject(value = "{\"type\":\"PaymentAuthorized\",\"published\":true}"))),
            @ApiResponse(responseCode = "400", description = "Unsupported type or a required field is missing",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation =
                            com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"bookingId is required for PaymentAuthorized\",\"path\":\"/dev/events\"}")))
    })
    @PostMapping
    public ResponseEntity<DevEventResponse> publish(@RequestBody JsonNode request) {
        IntegrationEventParser.ParsedEvent parsed = parser.parse(request);
        eventPublisher.publish(parsed.event());
        return ResponseEntity.accepted().body(new DevEventResponse(parsed.type(), true));
    }

    public record DevEventResponse(String type, boolean published) {
    }
}
