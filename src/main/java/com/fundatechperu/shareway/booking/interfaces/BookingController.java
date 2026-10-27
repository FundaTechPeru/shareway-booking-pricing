package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * Booking endpoints. The passenger always comes from the X-User-Id header, validated by the
 * API Gateway: this service performs no authentication of its own.
 */
@RestController
@RequestMapping("/bookings")
@Tag(name = "Bookings", description = "Seat reservations: create, query and cancel bookings.")
public class BookingController {

    private static final String NO_SEATS_MESSAGE = "El viaje ya no tiene asientos disponibles.";
    private static final String PASSENGER_HEADER_DOC =
            "Passenger identifier (UUID) delivered already validated by the API Gateway";

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(
            summary = "Create a booking (idempotent)",
            description = "Reserves one seat of the group with an optimistic lock and creates the booking in "
                    + "status REQUESTED in a single local transaction, records the estimated fare and publishes "
                    + "SeatHeld. The passenger comes from the X-User-Id header; repeating a request with the same "
                    + "Idempotency-Key returns the original booking instead of creating a new one.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Booking accepted, or replayed via Idempotency-Key",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class),
                            examples = @ExampleObject(value = "{\"bookingId\":\"3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\",\"status\":\"REQUESTED\"}"))),
            @ApiResponse(responseCode = "400", description = "groupId, requestId or X-User-Id is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Group not found in seat inventories, or no fare rule valid today",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = NO_SEATS_MESSAGE,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\":409,\"message\":\"" + NO_SEATS_MESSAGE + "\",\"timestamp\":\"2026-10-25T10:15:30\"}")))
    })
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(description = PASSENGER_HEADER_DOC, required = true,
                    example = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
            @RequestHeader(value = "X-User-Id", required = false) UUID passengerId,
            @Parameter(description = "Replay key: the same key returns the original booking without creating another",
                    example = "0a1b2c3d-booking-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BookingResponse response = bookingService.createBooking(request, passengerId, idempotencyKey);
        return ResponseEntity.accepted()
                .location(URI.create("/bookings/" + response.bookingId()))
                .body(response);
    }

    @Operation(summary = "Get a booking by id",
            description = "Returns the booking with its status in uppercase (REQUESTED, GROUPED, ...).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown booking id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> findById(
            @Parameter(description = "Booking identifier", required = true,
                    example = "3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f")
            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.findById(bookingId));
    }

    @Operation(summary = "Cancel a booking",
            description = "Moves the booking to CANCELLED and releases its seat. Only bookings in REQUESTED, "
                    + "GROUPED or CONFIRMED can be cancelled; cancelling twice answers 200 without changes. "
                    + "A cancellation closer than 30 minutes to the departure also creates a penalty of the 20 % "
                    + "of the estimated fare (minimum 1.00, reason LATE_CANCELLATION), reported as penaltyAmount. "
                    + "The passenger comes from the X-User-Id header delivered by the API Gateway; a different "
                    + "value answers 404 and a missing header answers 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled (idempotent)",
                    content = @Content(schema = @Schema(implementation = CancellationResponse.class),
                            examples = @ExampleObject(value = "{\"bookingId\":\"3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f\",\"status\":\"CANCELLED\",\"penaltyAmount\":4.27,\"currency\":\"PEN\"}"))),
            @ApiResponse(responseCode = "400", description = "X-User-Id header is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown booking id, or the booking belongs to another passenger",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The booking cannot be cancelled from its current status (boarded, in_progress or completed)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\":409,\"message\":\"La reserva ya no puede cancelarse en su estado actual: BOARDED\",\"timestamp\":\"2026-10-25T10:15:30\"}")))
    })
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<CancellationResponse> cancel(
            @Parameter(description = "Booking identifier", required = true,
                    example = "3f0f6a3c-2a3b-4c6d-9d1e-0a1b2c3d4e5f")
            @PathVariable UUID bookingId,
            @Parameter(description = PASSENGER_HEADER_DOC, required = true,
                    example = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
            @RequestHeader(value = "X-User-Id", required = false) UUID actorId) {
        return ResponseEntity.ok(bookingService.cancel(bookingId, actorId));
    }
}
