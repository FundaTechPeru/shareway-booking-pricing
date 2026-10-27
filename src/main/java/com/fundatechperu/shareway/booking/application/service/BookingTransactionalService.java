package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.*;
import com.fundatechperu.shareway.booking.domain.service.CancellationPolicy;
import com.fundatechperu.shareway.booking.domain.service.DepartureTimeProvider;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.model.Penalty;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.booking.pricing.domain.repository.PenaltyRepository;
import com.fundatechperu.shareway.booking.pricing.domain.service.GroupInfoProvider;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationRequested;
import com.fundatechperu.shareway.booking.shared.events.SeatHeld;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Local transactions of the booking lifecycle (each one atomic, retried by its caller on
 * optimistic-lock failures):
 *
 * 1. createOnce (POST /bookings): idempotent replay returns the existing booking untouched;
 *    otherwise one seat is held (optimistic lock via seat_inventories.version), the booking
 *    is created in REQUESTED, the estimated fare is recorded, the saga starts (running /
 *    AUTHORIZE_PAYMENT / deadline +payment timeout) and SeatHeld plus
 *    PaymentAuthorizationRequested are published.
 *
 * 2. cancelOnce (POST /bookings/{id}/cancel, Sprint 2): the owner cancels from requested,
 *    grouped or confirmed. Cancelling twice is a no-op 200; boarded, in_progress or
 *    completed answer 409. The seat is released, the booking becomes cancelled, the saga is
 *    compensated and BookingCancelled is published. A cancellation closer than the penalty
 *    window to the departure (DepartureTimeProvider) also creates the penalty: 20 % of the
 *    estimated fare with a 1.00 floor, reason LATE_CANCELLATION.
 */
@Service
public class BookingTransactionalService {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final FareService fareService;
    private final FareRepository fareRepository;
    private final PenaltyRepository penaltyRepository;
    private final CancellationPolicy cancellationPolicy;
    private final DepartureTimeProvider departureTimeProvider;
    private final GroupInfoProvider groupInfoProvider;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final long paymentTimeoutMinutes;
    private final Counter retryCounter;

    public BookingTransactionalService(BookingRepository bookingRepository,
                                       SeatInventoryRepository seatInventoryRepository,
                                       BookingSagaRepository bookingSagaRepository,
                                       FareService fareService,
                                       FareRepository fareRepository,
                                       PenaltyRepository penaltyRepository,
                                       CancellationPolicy cancellationPolicy,
                                       DepartureTimeProvider departureTimeProvider,
                                       GroupInfoProvider groupInfoProvider,
                                       EventPublisher eventPublisher,
                                       Clock clock,
                                       @Value("${booking.saga.payment-timeout-minutes:15}") long paymentTimeoutMinutes,
                                       MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.fareService = fareService;
        this.fareRepository = fareRepository;
        this.penaltyRepository = penaltyRepository;
        this.cancellationPolicy = cancellationPolicy;
        this.departureTimeProvider = departureTimeProvider;
        this.groupInfoProvider = groupInfoProvider;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
        this.retryCounter = meterRegistry.counter("booking.reservation.retries");
    }

    @Transactional
    public BookingResponse createOnce(CreateBookingRequest request, UUID passengerId, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Booking> replay = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (replay.isPresent()) return BookingResponse.from(replay.get());
        }

        UUID requestId = request.getRequestId();
        UUID groupId = request.getGroupId();
        if (bookingRepository.existsByRequestId(requestId))
            throw new BookingAlreadyExistsException("A booking already exists for requestId " + requestId);

        SeatInventory inventory = seatInventoryRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        inventory.reserveSeat();                       // throws NoSeatsAvailableException when 0 seats
        seatInventoryRepository.save(inventory);       // optimistic lock on seat_inventories.version

        Booking booking;
        try {
            booking = bookingRepository.save(Booking.create(requestId, groupId, passengerId, idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            throw new BookingAlreadyExistsException("A booking already exists for requestId " + requestId);
        }

        BookingSaga saga = BookingSaga.start(booking.getBookingId(), SagaStep.AUTHORIZE_PAYMENT,
                LocalDateTime.now(clock).plusMinutes(paymentTimeoutMinutes));
        bookingSagaRepository.save(saga);

        LocalDate today = LocalDate.now(clock);
        BigDecimal distanceKm = groupInfoProvider.distanceKm(groupId);
        int groupedPassengers = Math.max(1, inventory.getCapacity() - inventory.getAvailableSeats());
        fareService.recordFare(booking.getBookingId(), Fare.ESTIMATED, today, distanceKm, groupedPassengers);

        eventPublisher.publish(new SeatHeld(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), groupId, passengerId));
        eventPublisher.publish(new PaymentAuthorizationRequested(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), groupId, passengerId, today, distanceKm, groupedPassengers));

        return BookingResponse.from(booking);
    }

    /**
     * Cancels one booking on behalf of its passenger (the X-User-Id delivered by the API
     * Gateway). Already cancelled bookings answer 200 without touching the seat a second
     * time; boarded, in_progress or completed bookings are rejected with a 409 message.
     */
    @Transactional
    public CancellationResponse cancelOnce(UUID bookingId, UUID actorId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!actorId.equals(booking.getPassengerId())) {
            throw new BookingNotFoundException(bookingId);   // not theirs: does not exist for them
        }

        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CANCELLED) {
            return new CancellationResponse(bookingId, BookingStatus.CANCELLED, null, null);
        }
        if (status != BookingStatus.REQUESTED && status != BookingStatus.GROUPED
                && status != BookingStatus.CONFIRMED) {
            throw new BookingCannotBeCancelledException(
                    "La reserva ya no puede cancelarse en su estado actual: " + status);
        }

        BigDecimal penaltyAmount = createLateCancellationPenaltyIfAny(booking);

        booking.cancel();
        bookingRepository.save(booking);

        SeatInventory inventory = seatInventoryRepository.findByIdForUpdate(booking.getGroupId())
                .orElseThrow(() -> new GroupNotFoundException(booking.getGroupId()));
        inventory.releaseSeat();                        // available_seats + 1, never above capacity
        seatInventoryRepository.save(inventory);

        bookingSagaRepository.findByBookingId(bookingId).ifPresent(saga -> {
            if (saga.getStatus() != BookingSaga.Status.COMPENSATED) {
                saga.compensate();
                bookingSagaRepository.save(saga);
            }
        });

        eventPublisher.publish(new BookingCancelled(UUID.randomUUID(), clock.instant(),
                bookingId, booking.getGroupId(), booking.getPassengerId(), penaltyAmount != null));

        return new CancellationResponse(bookingId, BookingStatus.CANCELLED, penaltyAmount,
                penaltyAmount == null ? null : fareOf(bookingId).map(Fare::currency).orElse(null));
    }

    /** 20 % of the estimated fare (1.00 floor) only inside the 30-minute window. */
    private BigDecimal createLateCancellationPenaltyIfAny(Booking booking) {
        Instant departureAt = departureTimeProvider.departureAt(booking.getGroupId());
        if (departureAt == null
                || !cancellationPolicy.isPenaltyApplicable(clock.instant(), departureAt)) {
            return null;
        }
        Optional<Fare> estimated = fareOf(booking.getBookingId());
        if (estimated.isEmpty()) return null;   // nothing to charge against: no penalty
        BigDecimal amount = cancellationPolicy.penaltyFor(estimated.get().amount());
        penaltyRepository.save(new Penalty(UUID.randomUUID(), booking.getBookingId(), amount,
                Penalty.LATE_CANCELLATION, LocalDateTime.now(clock)));
        return amount;
    }

    private Optional<Fare> fareOf(UUID bookingId) {
        return fareRepository.findByBookingIdAndFareType(bookingId, Fare.ESTIMATED);
    }

    public void incrementRetry() {
        retryCounter.increment();
    }
}
