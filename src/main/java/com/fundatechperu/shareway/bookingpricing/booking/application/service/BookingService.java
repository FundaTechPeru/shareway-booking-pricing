package com.fundatechperu.shareway.bookingpricing.booking.application.service;

import com.fundatechperu.shareway.bookingpricing.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.bookingpricing.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.*;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.TripGroup;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.*;
import com.fundatechperu.shareway.bookingpricing.booking.domain.service.CancellationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingTransactionalService transactionalService;
    private final BookingRepository bookingRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripRequestRepository tripRequestRepository;
    private final int maxAttempts;
    private final long backoffMs;
    private final CancellationPolicy cancellationPolicy;
    private final Clock clock;
    public BookingService(BookingTransactionalService transactionalService, BookingRepository bookingRepository,
                          TripGroupRepository tripGroupRepository,
                          TripRequestRepository tripRequestRepository,
                          @Value("${booking.reservation.max-attempts:3}") int maxAttempts,
                          @Value("${booking.reservation.backoff-ms:20}") long backoffMs,
                          CancellationPolicy cancellationPolicy, Clock clock) {
        this.transactionalService = transactionalService; this.bookingRepository = bookingRepository;
        this.tripGroupRepository = tripGroupRepository; this.maxAttempts = maxAttempts; this.backoffMs = backoffMs;
        this.tripRequestRepository = tripRequestRepository;
        this.cancellationPolicy = cancellationPolicy; this.clock = clock;
    }
    public BookingResponse createBooking(CreateBookingRequest request) {
        if (request == null || request.getRequestId() == null || request.getGroupId() == null)
            throw new InvalidBookingRequestException("requestId and groupId are required");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { return transactionalService.createOnce(request); }
            catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException exception) {
                transactionalService.incrementRetry();
                if (attempt == maxAttempts) {
                    TripGroup current = tripGroupRepository.findById(request.getGroupId())
                            .orElseThrow(() -> new TripGroupNotFoundException(request.getGroupId()));
                    if (current.getAvailableSeats() == 0) throw new NoSeatsAvailableException();
                    throw new ConcurrentReservationException();
                }
                try { Thread.sleep(backoffMs + (long) (Math.random() * 10)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ConcurrentReservationException(); }
            }
        }
        throw new ConcurrentReservationException();
    }
    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(BookingResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId, UUID actorId, boolean admin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (!admin && !tripRequestRepository.findPassengerIdById(booking.getRequestId())
                .filter(actorId::equals).isPresent()) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }
        return BookingResponse.from(booking);
    }
    public record CancellationResponse(BookingResponse booking, boolean penaltyApplicable) {}
    @Transactional
    public CancellationResponse cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        TripGroup group = tripGroupRepository.findById(booking.getGroupId())
                .orElseThrow(() -> new TripGroupNotFoundException(booking.getGroupId()));
        boolean applicable = group.getDepartureAt() != null
                && cancellationPolicy.isPenaltyApplicable(clock.instant(),
                group.getDepartureAt().atZone(ZoneId.of("UTC")).toInstant());
        if (booking.getStatus() != com.fundatechperu.shareway.bookingpricing.booking.domain.model.BookingStatus.CANCELLED) {
            booking.cancel(); group.releaseSeat(); tripGroupRepository.save(group);
            bookingRepository.save(booking);
        }
        return new CancellationResponse(BookingResponse.from(booking), applicable);
    }

    @Transactional
    public CancellationResponse cancel(UUID bookingId, UUID actorId, boolean admin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (!admin && !tripRequestRepository.findPassengerIdById(booking.getRequestId())
                .filter(actorId::equals).isPresent()) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }
        return cancel(bookingId);
    }
}
