package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.domain.exception.*;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.*;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.service.GroupInfoProvider;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The single local transaction of POST /bookings:
 * 1. Idempotency-Key replay: an existing booking is returned untouched (no seat, no saga,
 *    no fare, no events).
 * 2. Otherwise: hold one seat (optimistic lock via seat_inventories.version), create the
 *    booking in status REQUESTED with the X-User-Id passenger, record the estimated fare,
 *    start the booking saga (running / AUTHORIZE_PAYMENT / deadline +15 min) and publish
 *    SeatHeld plus PaymentAuthorizationRequested. If anything fails, everything rolls back
 *    together.
 */
@Service
public class BookingTransactionalService {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final FareService fareService;
    private final GroupInfoProvider groupInfoProvider;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final long paymentTimeoutMinutes;
    private final Counter retryCounter;

    public BookingTransactionalService(BookingRepository bookingRepository,
                                       SeatInventoryRepository seatInventoryRepository,
                                       BookingSagaRepository bookingSagaRepository,
                                       FareService fareService,
                                       GroupInfoProvider groupInfoProvider,
                                       EventPublisher eventPublisher,
                                       Clock clock,
                                       @Value("${booking.saga.payment-timeout-minutes:15}") long paymentTimeoutMinutes,
                                       MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.fareService = fareService;
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

    public void incrementRetry() {
        retryCounter.increment();
    }
}
