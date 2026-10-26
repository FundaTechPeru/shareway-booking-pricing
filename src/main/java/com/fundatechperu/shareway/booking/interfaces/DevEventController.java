package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.booking.shared.events.*;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manual testing of the in-memory event bus: publishes a simulated event through the same
 * EventPublisher used by the application. Every consumed event of the lifecycle can be
 * simulated (Sprint 2). Only registered with the "dev" profile
 * (--spring.profiles.active=dev); the endpoint does not exist in any other profile.
 */
@RestController
@RequestMapping("/dev/events")
@Profile("dev")
@Tag(name = "Dev", description = "Simulated events for manual testing. Available only with the 'dev' profile.")
public class DevEventController {

    private static final String SUPPORTED_TYPES =
            "TripGroupOpened, PaymentAuthorized, PaymentAuthorizationFailed, GroupCompleted, "
                    + "ProposalAccepted, RouteUpdated, BoardingValidated, TripStarted, TripCompleted";

    private final EventPublisher eventPublisher;

    public DevEventController(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Operation(summary = "Publish a simulated event",
            description = "Dispatches the event to the same handlers that consume it in production. "
                    + "Supported types: " + SUPPORTED_TYPES + ".")
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
            case "TripGroupOpened" -> {
                requireGroupId(request);
                requireCapacity(request);
                yield new TripGroupOpened(eventId, occurredAt, request.groupId(), request.capacity());
            }
            case "PaymentAuthorized" -> {
                requireBookingId(request);
                yield new PaymentAuthorized(eventId, occurredAt, request.bookingId(), UUID.randomUUID());
            }
            case "PaymentAuthorizationFailed" -> {
                requireBookingId(request);
                String reason = request.reason() == null ? "simulated failure" : request.reason();
                yield new PaymentAuthorizationFailed(eventId, occurredAt, request.bookingId(), reason);
            }
            case "GroupCompleted" -> {
                requireGroupId(request);
                yield new GroupCompleted(eventId, occurredAt, request.groupId());
            }
            case "ProposalAccepted" -> {
                requireGroupId(request);
                if (request.driverId() == null)
                    throw new InvalidBookingRequestException("driverId is required for ProposalAccepted");
                yield new ProposalAccepted(eventId, occurredAt, request.groupId(), request.driverId());
            }
            case "RouteUpdated" -> {
                requireGroupId(request);
                requireDistanceKm(request);
                yield new RouteUpdated(eventId, occurredAt, request.groupId(), request.distanceKm());
            }
            case "BoardingValidated" -> {
                requireBookingId(request);
                yield new BoardingValidated(eventId, occurredAt, request.bookingId());
            }
            case "TripStarted" -> {
                requireGroupId(request);
                yield new TripStarted(eventId, occurredAt, request.groupId());
            }
            case "TripCompleted" -> {
                requireGroupId(request);
                requireDistanceKm(request);
                yield new TripCompleted(eventId, occurredAt, request.groupId(), request.distanceKm());
            }
            default -> throw new InvalidBookingRequestException("Unsupported event type: " + request.type()
                    + ". Supported: " + SUPPORTED_TYPES);
        };
    }

    private void requireBookingId(DevEventRequest request) {
        if (request.bookingId() == null)
            throw new InvalidBookingRequestException("bookingId is required for " + request.type());
    }

    private void requireGroupId(DevEventRequest request) {
        if (request.groupId() == null)
            throw new InvalidBookingRequestException("groupId is required for " + request.type());
    }

    private void requireCapacity(DevEventRequest request) {
        if (request.capacity() == null || request.capacity() < 1)
            throw new InvalidBookingRequestException("capacity (>= 1) is required for TripGroupOpened");
    }

    private void requireDistanceKm(DevEventRequest request) {
        if (request.distanceKm() == null || request.distanceKm().signum() < 0)
            throw new InvalidBookingRequestException("distanceKm (>= 0) is required for " + request.type());
    }

    public record DevEventRequest(@NotBlank String type, UUID bookingId, UUID groupId, UUID driverId,
                                  Integer capacity, BigDecimal distanceKm, String reason) {
    }

    public record DevEventResponse(String type, boolean published) {
    }
}
