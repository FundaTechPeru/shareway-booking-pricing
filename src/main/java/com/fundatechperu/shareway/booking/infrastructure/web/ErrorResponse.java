package com.fundatechperu.shareway.booking.infrastructure.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Uniform error body of every failed request: {timestamp, status, error, message, path}.
 * The path is the request URI as received (including the /api/v1 prefix), so a caller
 * behind the API Gateway can tell which route produced the answer.
 */
@Schema(description = "Uniform error payload returned by every non-2xx response")
public record ErrorResponse(
        @Schema(description = "Moment the error was produced", example = "2026-11-20T10:15:30")
        LocalDateTime timestamp,
        @Schema(description = "HTTP status code", example = "409")
        int status,
        @Schema(description = "HTTP reason phrase", example = "Conflict")
        String error,
        @Schema(description = "Human-readable explanation", example = "El viaje ya no tiene asientos disponibles.")
        String message,
        @Schema(description = "Request path that produced the error", example = "/api/v1/bookings")
        String path) {

    public ErrorResponse(int status, String error, String message, String path) {
        this(LocalDateTime.now(), status, error, message, path);
    }
}
