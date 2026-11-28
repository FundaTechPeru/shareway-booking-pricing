package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.infrastructure.OpenApiConfiguration;
import com.fundatechperu.shareway.booking.infrastructure.security.AuthenticatedUser;
import com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Booking endpoints. The caller is the subject of the Bearer JWT (HS256, JWT_SECRET) and its
 * role the {@code role} claim; X-User-Id is not read any more.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Seat reservations: create, list, query and cancel bookings.")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public class BookingController {

    private static final String NO_SEATS_MESSAGE = "The trip has no seats available.";
    private static final String BOOKING_EXAMPLE = "{\"bookingId\":\"3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\","
            + "\"groupId\":\"7a2b3c4d-5e6f-4a1b-9c8d-7e6f5a4b3c2d\",\"requestId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"status\":\"REQUESTED\",\"estimatedAmount\":21.34,\"currency\":\"PEN\"}";
    private static final String CONFIRMED_EXAMPLE = "{\"bookingId\":\"3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\","
            + "\"groupId\":\"7a2b3c4d-5e6f-4a1b-9c8d-7e6f5a4b3c2d\",\"requestId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"status\":\"CONFIRMED\",\"estimatedAmount\":21.34,\"currency\":\"PEN\",\"boardingPin\":\"004271\","
            + "\"qrToken\":\"9f86d081884c7d659a2feaa0c55ad015\"}";
    private static final String UNAUTHORIZED_EXAMPLE = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":401,"
            + "\"error\":\"Unauthorized\",\"message\":\"Missing, invalid or expired access token\",\"path\":\"/api/v1/bookings\"}";
    private static final String FORBIDDEN_EXAMPLE = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":403,"
            + "\"error\":\"Forbidden\",\"message\":\"The role of the access token cannot use this operation\","
            + "\"path\":\"/api/v1/bookings\"}";

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(
            summary = "Create a booking (idempotent, PASSENGER)",
            description = "Reserves one seat of the group with an optimistic lock and creates the booking in "
                    + "status REQUESTED in a single local transaction, records the estimated fare and queues the "
                    + "payment authorization of that amount in Payments (paymentMethodId is optional: Payments "
                    + "uses the passenger's most recent active card when it is omitted). The passenger is the "
                    + "subject of the JWT; repeating a request with the same Idempotency-Key returns the original "
                    + "booking instead of creating a new one.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Booking accepted, or replayed via Idempotency-Key",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BookingResponse.class),
                            examples = @ExampleObject(value = BOOKING_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "groupId or requestId is missing or malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Invalid request\",\"path\":\"/api/v1/bookings\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "The token does not carry the PASSENGER role",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Group not found in seat inventories, or no fare rule valid today",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Group not found: 7a2b3c4d-5e6f-4a1b-9c8d-7e6f5a4b3c2d\",\"path\":\"/api/v1/bookings\"}"))),
            @ApiResponse(responseCode = "409", description = NO_SEATS_MESSAGE,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":409,\"error\":\"Conflict\",\"message\":\"" + NO_SEATS_MESSAGE + "\",\"path\":\"/api/v1/bookings\"}")))
    })
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Booking to request: the trip group to join, the caller-generated replay key and, "
                            + "optionally, the payment method",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateBookingRequest.class),
                            examples = @ExampleObject(value = """
                                    {"groupId":"7a2b3c4d-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
                                     "requestId":"22222222-2222-2222-2222-222222222222",
                                     "paymentMethodId":"5c4b3a29-1807-4f6e-8d5c-4b3a29180765"}""")))
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Replay key: the same key returns the original booking without creating another",
                    example = "0a1b2c3d-booking-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BookingResponse response = bookingService.createBooking(request, user.id(), idempotencyKey);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/bookings/" + response.bookingId()))
                .body(response);
    }

    @Operation(summary = "List bookings (PASSENGER: own, ADMIN: all)",
            description = "A PASSENGER gets their own bookings and an ADMIN every booking, newest first. "
                    + "boardingPin and qrToken only appear on the caller's own confirmed bookings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookings listed",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = BookingResponse.class)),
                            examples = @ExampleObject(value = "[" + CONFIRMED_EXAMPLE + "]"))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "The role is neither PASSENGER nor ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    })
    @GetMapping
    public List<BookingResponse> findAll(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return bookingService.findAll(user.id(), user.isAdmin());
    }

    @Operation(summary = "Get a booking by id (owner or ADMIN)",
            description = "Returns the booking with its status in uppercase (REQUESTED, GROUPED, ...). Once the "
                    + "booking is CONFIRMED (or further) its owner also gets boardingPin and qrToken, to show one of "
                    + "them when boarding. A booking of another passenger answers 404 unless the caller is an ADMIN "
                    + "(who never gets the boarding codes).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BookingResponse.class),
                            examples = @ExampleObject(value = CONFIRMED_EXAMPLE))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Unknown booking id, or the booking belongs to another passenger",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Booking not found: 3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\",\"path\":\"/api/v1/bookings/3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\"}")))
    })
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> findById(
            @Parameter(description = "Booking identifier", required = true,
                    example = "3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f")
            @PathVariable UUID bookingId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(bookingService.findById(bookingId, user.id(), user.isAdmin()));
    }

    @Operation(summary = "Cancel a booking (owner)",
            description = "Moves the booking to CANCELLED and releases its seat. Only bookings in REQUESTED, "
                    + "GROUPED or CONFIRMED can be cancelled; cancelling twice answers 200 without changes. "
                    + "A cancellation closer than 30 minutes to the departure also creates a penalty of the 20 % "
                    + "of the estimated fare (minimum 1.00, reason LATE_CANCELLATION), reported as penaltyAmount. "
                    + "When the payment was authorized, Payments is told (reason PASSENGER_CANCELLED). Only the "
                    + "owner (subject of the JWT) can cancel; anybody else gets 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled (idempotent)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CancellationResponse.class),
                            examples = @ExampleObject(value = "{\"bookingId\":\"3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\",\"status\":\"CANCELLED\",\"penaltyAmount\":4.27,\"currency\":\"PEN\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Unknown booking id, or the booking belongs to another passenger",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Booking not found: 3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\",\"path\":\"/api/v1/bookings/3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f/cancel\"}"))),
            @ApiResponse(responseCode = "409", description = "The booking cannot be cancelled from its current status (boarded, in_progress or completed)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":409,\"error\":\"Conflict\",\"message\":\"The booking cannot be cancelled from its current status: BOARDED\",\"path\":\"/api/v1/bookings/3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f/cancel\"}")))
    })
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<CancellationResponse> cancel(
            @Parameter(description = "Booking identifier", required = true,
                    example = "3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f")
            @PathVariable UUID bookingId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(bookingService.cancel(bookingId, user.id()));
    }
}
