package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.web.controller;

import com.fundatechperu.shareway.bookingpricing.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.bookingpricing.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.bookingpricing.booking.application.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.getBookingId())).body(response);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> findById(@PathVariable UUID bookingId,
                                                    Authentication authentication) {
        return ResponseEntity.ok(bookingService.findById(bookingId, subjectId(authentication), isAdmin(authentication)));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingService.CancellationResponse> cancel(@PathVariable UUID bookingId,
                                                                        Authentication authentication) {
        return ResponseEntity.ok(bookingService.cancel(bookingId, subjectId(authentication), isAdmin(authentication)));
    }

    private UUID subjectId(Authentication authentication) {
        return UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
