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

import java.util.List;
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
     * attempt the answer is 409 "The trip has no seats available." for both cases:
     * no seats left and contention over the same seat.
     */
    public BookingResponse createBooking(CreateBookingRequest request, UUID passengerId, String idempotencyKey) {
        if (request == null || request.getRequestId() == null || request.getGroupId() == null)
            throw new InvalidBookingRequestException("requestId and groupId are required");
        if (passengerId == null)
            throw new InvalidBookingRequestException("An authenticated passenger is required");
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

    /**
     * One booking as seen by the requester: its owner (with the boarding codes once it is
     * confirmed) or an admin (without them). Anybody else gets a 404: for them the booking
     * does not exist.
     */
    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId, UUID requesterId, boolean admin) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> admin || booking.getPassengerId().equals(requesterId))
                .map(booking -> BookingResponse.viewedBy(booking, requesterId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /** The bookings of the requester, newest first; an admin sees every booking. */
    @Transactional(readOnly = true)
    public List<BookingResponse> findAll(UUID requesterId, boolean admin) {
        return (admin ? bookingRepository.findAll() : bookingRepository.findByPassengerId(requesterId))
                .stream().map(booking -> BookingResponse.viewedBy(booking, requesterId)).toList();
    }

    /**
     * Cancellation by the passenger of the JWT. There is no admin bypass: a different
     * passenger is a 404 (the booking does not exist as far as that passenger is concerned).
     * The cancellation transaction is retried on optimistic-lock failures (many passengers
     * cancelling the same group at once).
     */
    public CancellationResponse cancel(UUID bookingId, UUID actorId) {
        if (actorId == null)
            throw new InvalidBookingRequestException("An authenticated passenger is required");
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
