package com.fundatechperu.shareway.booking.infrastructure.web;

import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.exception.InvalidFareException;
import jakarta.validation.ConstraintViolationException;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Client error: malformed or incomplete request (400). */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            InvalidBookingRequestException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Unknown resource, including unknown URL paths (404). */
    @ExceptionHandler({FareNotFoundException.class, GroupNotFoundException.class, BookingNotFoundException.class,
            NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception exception) {
        return response(HttpStatus.NOT_FOUND, messageFor(exception));
    }

    /** Wrong HTTP method for an existing path (405). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(Exception exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage());
    }

    /** Unsupported request content type (415). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(Exception exception) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    /** Conflicting state: no seats left, duplicate booking, invalid lifecycle transition (409). */
    @ExceptionHandler({BookingAlreadyExistsException.class, NoSeatsAvailableException.class,
            InvalidBookingTransitionException.class, BookingCannotBeCancelledException.class,
            InvalidFareException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /** NoResourceFoundException has no useful message; keep the contract {status, message, timestamp}. */
    private String messageFor(Exception exception) {
        String message = exception.getMessage();
        return (message == null || message.isBlank()) ? "Resource not found" : message;
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
