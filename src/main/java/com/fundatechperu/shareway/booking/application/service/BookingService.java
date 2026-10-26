package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Booking use cases outside the transactions: request validation, optimistic-lock retries
 * and the delegation to the atomic operations of {@link BookingTransactionalService}.
 */
@Service
public class BookingService {
    private final BookingTransactionalService transactionalService;
    private final BookingRepository bookingRepository;
    private final int maxAttempts;
    private final long backoffMs;

    public BookingService(BookingTransactionalService transactionalService, BookingRepository bookingRepository,
                          @Value("${booking.reservation.max-attempts:3}") int maxAttempts,
                          @Value("${booking.reservation.backoff-ms:20}") long backoffMs) {
        this.transactionalService = transactionalService;
        this.bookingRepository = bookingRepository;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /**
     * Runs the reservation transaction, retrying on optimistic-lock failures. After the last
     * attempt the answer is 409 "El viaje ya no tiene asientos disponibles." for both cases:
     * no seats left and contention over the same seat.
     */
    public BookingResponse createBooking(CreateBookingRequest request, UUID passengerId, String idempotencyKey) {
        if (request == null || request.getRequestId() == null || request.getGroupId() == null)
            throw new InvalidBookingRequestException("requestId and groupId are required");
        if (passengerId == null)
            throw new InvalidBookingRequestException("X-User-Id header is required");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return transactionalService.createOnce(request, passengerId, idempotencyKey);
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException exception) {
                transactionalService.incrementRetry();
                if (attempt == maxAttempts) throw new NoSeatsAvailableException();
                try {
                    Thread.sleep(backoffMs + (long) (Math.random() * 10));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new NoSeatsAvailableException();
                }
            }
        }
        throw new NoSeatsAvailableException();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /**
     * Cancellation as the API Gateway hands it over: the passenger is whoever the validated
     * X-User-Id header says. No JWT is read inside this service, so there is no admin bypass.
     * A missing header is a 400; a different passenger is a 404 (the booking does not exist
     * as far as that passenger is concerned). The cancellation transaction is retried on
     * optimistic-lock failures (many passengers cancelling the same group at once).
     */
    public CancellationResponse cancel(UUID bookingId, UUID actorId) {
        if (actorId == null)
            throw new InvalidBookingRequestException("X-User-Id header is required");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return transactionalService.cancelOnce(bookingId, actorId);
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException exception) {
                transactionalService.incrementRetry();
                if (attempt == maxAttempts) throw exception;
                try {
                    Thread.sleep(backoffMs + (long) (Math.random() * 10));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Cancellation of " + bookingId + " could not be retried");
    }
}
