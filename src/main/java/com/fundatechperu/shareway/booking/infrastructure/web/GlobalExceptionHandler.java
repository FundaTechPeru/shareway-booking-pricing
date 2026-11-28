package com.fundatechperu.shareway.booking.infrastructure.web;

import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.exception.InvalidFareException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single place where every exception becomes an HTTP answer. The contract:
 * 400 for a malformed or incomplete request, 404 for an unknown resource, 405/415 for
 * a wrong method or media type, 409 for a violated business rule or invalid state,
 * 503 for an integration event that must be sent again, and 500 only for genuinely
 * unexpected failures (which are logged with their stack trace).
 * No business rule may reach the generic 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Client error: malformed or incomplete request (400). */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            InvalidBookingRequestException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, messageOr(exception, "Invalid request"), request);
    }

    /** Unknown resource, including unknown URL paths (404). */
    @ExceptionHandler({FareNotFoundException.class, GroupNotFoundException.class, BookingNotFoundException.class,
            NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, messageOr(exception, "Resource not found"), request);
    }

    /** Wrong HTTP method for an existing path (405). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, messageOr(exception, "Method not allowed"), request);
    }

    /** Unsupported request content type (415). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, messageOr(exception, "Unsupported media type"), request);
    }

    /** Conflicting state: no seats left, duplicate booking, invalid lifecycle transition (409). */
    @ExceptionHandler({BookingAlreadyExistsException.class, NoSeatsAvailableException.class,
            InvalidBookingTransitionException.class, BookingCannotBeCancelledException.class,
            InvalidFareException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, messageOr(exception, "Conflicting state"), request);
    }

    /** An integration event could not be processed now; the sender retries (503). */
    @ExceptionHandler(EventProcessingException.class)
    public ResponseEntity<ErrorResponse> serviceUnavailable(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    /** Genuinely unexpected failure (500): the details go to the log, never to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception exception, HttpServletRequest request) {
        log.error("Unhandled {} on {} {}", exception.getClass().getSimpleName(),
                request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    /** NoResourceFoundException has no useful message; keep a readable default instead. */
    private String messageOr(Exception exception, String fallback) {
        String message = exception.getMessage();
        return (message == null || message.isBlank()) ? fallback : message;
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
