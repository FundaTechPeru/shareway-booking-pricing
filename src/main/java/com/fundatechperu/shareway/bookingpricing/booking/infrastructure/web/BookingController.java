package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.web;

import com.fundatechperu.shareway.bookingpricing.booking.application.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.application.service.BookingService;
import com.fundatechperu.shareway.bookingpricing.booking.domain.dto.BookingRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Operaciones del flujo de reservas")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/health")
    @Operation(summary = "Verificar disponibilidad del bounded context de bookings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio disponible",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Booking service is healthy")))
    })
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Booking service is healthy");
    }

    @PostMapping
    @Operation(summary = "Crear una reserva")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva creada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Booking.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    public ResponseEntity<Booking> createBooking(@RequestBody BookingRequestDto request) {
        return ResponseEntity.ok(bookingService.createBooking(request));
    }
}
