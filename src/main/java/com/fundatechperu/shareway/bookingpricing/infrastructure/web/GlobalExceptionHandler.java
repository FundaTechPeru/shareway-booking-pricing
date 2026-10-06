package com.fundatechperu.shareway.bookingpricing.infrastructure.web;

import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.*;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.InvalidFareException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.InvalidFareRuleException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            InvalidBookingRequestException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({FareNotFoundException.class, TripRequestNotFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({BookingAlreadyExistsException.class, NoSeatsAvailableException.class,
            InvalidBookingTransitionException.class, TripGroupNotFoundException.class,
            InvalidFareException.class, InvalidFareRuleException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
