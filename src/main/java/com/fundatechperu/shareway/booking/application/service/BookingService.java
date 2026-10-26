package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.*;
import com.fundatechperu.shareway.booking.domain.service.CancellationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingTransactionalService transactionalService;
    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final int maxAttempts;
    private final long backoffMs;
    private final CancellationPolicy cancellationPolicy;
    private final Clock clock;

    public BookingService(BookingTransactionalService transactionalService, BookingRepository bookingRepository,
                          SeatInventoryRepository seatInventoryRepository,
                          @Value("${booking.reservation.max-attempts:3}") int maxAttempts,
                          @Value("${booking.reservation.backoff-ms:20}") long backoffMs,
                          CancellationPolicy cancellationPolicy, Clock clock) {
        this.transactionalService = transactionalService;
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.cancellationPolicy = cancellationPolicy;
        this.clock = clock;
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

    public record CancellationResponse(BookingResponse booking, boolean penaltyApplicable) {}

    @Transactional
    public CancellationResponse cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        SeatInventory inventory = seatInventoryRepository.findById(booking.getGroupId())
                .orElseThrow(() -> new GroupNotFoundException(booking.getGroupId()));
        // departure_at lives in Driver Operations & Routing, not in this schema: while it is
        // unavailable the 30-minute window cannot be evaluated, so no penalty is flagged.
        Instant departureAt = null;
        boolean applicable = departureAt != null && cancellationPolicy.isPenaltyApplicable(clock.instant(), departureAt);
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.cancel();
            inventory.releaseSeat();
            seatInventoryRepository.save(inventory);
            bookingRepository.save(booking);
        }
        return new CancellationResponse(BookingResponse.from(booking), applicable);
    }

    /**
     * Cancellation as the API Gateway hands it over: the passenger is whoever the validated
     * X-User-Id header says. No JWT is read inside this service, so there is no admin bypass.
     * A missing header is a 400; a different passenger is a 404 (the booking does not exist
     * as far as that passenger is concerned).
     */
    @Transactional
    public CancellationResponse cancel(UUID bookingId, UUID actorId) {
        if (actorId == null)
            throw new InvalidBookingRequestException("X-User-Id header is required");
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!actorId.equals(booking.getPassengerId())) {
            throw new BookingNotFoundException(bookingId);
        }
        return cancel(bookingId);
    }
}
