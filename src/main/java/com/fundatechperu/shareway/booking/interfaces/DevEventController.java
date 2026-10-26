package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Manual testing of the in-memory event bus: publishes a simulated event through the same
 * EventPublisher used by the application. Only registered with the "dev" profile
 * (--spring.profiles.active=dev); the endpoint does not exist in any other profile.
 */
@RestController
@RequestMapping("/dev/events")
@Profile("dev")
@Tag(name = "Dev", description = "Simulated events for manual testing. Available only with the 'dev' profile.")
public class DevEventController {

    private final EventPublisher eventPublisher;

    public DevEventController(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Operation(summary = "Publish a simulated event",
            description = "Dispatches the event to the same handlers that consume it in production "
                    + "(PaymentAuthorized, PaymentAuthorizationFailed, TripGroupOpened).")
    @PostMapping
    public ResponseEntity<DevEventResponse> publish(@Valid @RequestBody DevEventRequest request) {
        Object event = toEvent(request);
        eventPublisher.publish(event);
        return ResponseEntity.accepted().body(new DevEventResponse(request.type(), true));
    }

    private Object toEvent(DevEventRequest request) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        return switch (request.type()) {
            case "PaymentAuthorized" -> {
                requireBookingId(request);
                yield new PaymentAuthorized(eventId, occurredAt, request.bookingId(), UUID.randomUUID());
            }
            case "PaymentAuthorizationFailed" -> {
                requireBookingId(request);
                String reason = request.reason() == null ? "simulated failure" : request.reason();
                yield new PaymentAuthorizationFailed(eventId, occurredAt, request.bookingId(), reason);
            }
            case "TripGroupOpened" -> {
                if (request.groupId() == null)
                    throw new InvalidBookingRequestException("groupId is required for TripGroupOpened");
                if (request.capacity() == null || request.capacity() < 1)
                    throw new InvalidBookingRequestException("capacity (>= 1) is required for TripGroupOpened");
                yield new TripGroupOpened(eventId, occurredAt, request.groupId(), request.capacity());
            }
            default -> throw new InvalidBookingRequestException("Unsupported event type: " + request.type()
                    + ". Supported: PaymentAuthorized, PaymentAuthorizationFailed, TripGroupOpened");
        };
    }

    private void requireBookingId(DevEventRequest request) {
        if (request.bookingId() == null)
            throw new InvalidBookingRequestException("bookingId is required for " + request.type());
    }

    public record DevEventRequest(@NotBlank String type, UUID bookingId, UUID groupId, Integer capacity,
                                  String reason) {
    }

    public record DevEventResponse(String type, boolean published) {
    }
}
