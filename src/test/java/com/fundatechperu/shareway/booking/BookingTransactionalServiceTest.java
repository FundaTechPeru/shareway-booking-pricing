package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.application.service.BookingTransactionalService;
import com.fundatechperu.shareway.booking.domain.exception.BookingAlreadyExistsException;
import com.fundatechperu.shareway.booking.domain.exception.BookingCannotBeCancelledException;
import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.GroupNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.domain.service.CancellationPolicy;
import com.fundatechperu.shareway.booking.domain.service.DepartureTimeProvider;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.model.Penalty;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.booking.pricing.domain.repository.PenaltyRepository;
import com.fundatechperu.shareway.booking.pricing.infrastructure.provider.FakeGroupInfoProvider;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationRequested;
import com.fundatechperu.shareway.booking.shared.events.SeatHeld;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The single local transaction of POST /bookings: idempotent replay, seat hold with
 * optimistic lock, booking in REQUESTED, estimated fare, saga running/AUTHORIZE_PAYMENT
 * with a +15 min deadline, and the SeatHeld + PaymentAuthorizationRequested events.
 */
class BookingTransactionalServiceTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PASSENGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    private static final long PAYMENT_TIMEOUT_MINUTES = 15;

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final SeatInventoryRepository seatInventoryRepository = mock(SeatInventoryRepository.class);
    private final BookingSagaRepository bookingSagaRepository = mock(BookingSagaRepository.class);
    private final FareService fareService = mock(FareService.class);
    private final FareRepository fareRepository = mock(FareRepository.class);
    private final PenaltyRepository penaltyRepository = mock(PenaltyRepository.class);
    private final DepartureTimeProvider departureTimeProvider = mock(DepartureTimeProvider.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final CancellationPolicy cancellationPolicy =
            new CancellationPolicy(30, BigDecimal.valueOf(20), new BigDecimal("1.00"));

    private BookingTransactionalService service;

    @BeforeEach
    void setUp() {
        service = new BookingTransactionalService(bookingRepository, seatInventoryRepository,
                bookingSagaRepository, fareService, fareRepository, penaltyRepository, cancellationPolicy,
                departureTimeProvider, new FakeGroupInfoProvider(), eventPublisher,
                FIXED_CLOCK, PAYMENT_TIMEOUT_MINUTES, new SimpleMeterRegistry());
    }

    private CreateBookingRequest request() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setRequestId(REQUEST_ID);
        request.setGroupId(GROUP_ID);
        return request;
    }

    @Test
    void sameIdempotencyKeyReplaysTheBookingWithoutSideEffects() {
        Booking existing = Booking.restore(UUID.randomUUID(), REQUEST_ID, GROUP_ID, PASSENGER_ID,
                "idem-key", BookingStatus.CONFIRMED, null, null, 3L, null);
        when(bookingRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.of(existing));

        BookingResponse response = service.createOnce(request(), PASSENGER_ID, "idem-key");

        assertEquals(existing.getBookingId(), response.bookingId());
        assertEquals(BookingStatus.CONFIRMED, response.status());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(seatInventoryRepository, bookingSagaRepository, fareService, eventPublisher);
    }

    @Test
    void holdsSeatCreatesBookingSagaFareAndPublishesBothEvents() {
        SeatInventory inventory = SeatInventory.restore(GROUP_ID, 4, 4, 0L, null);
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.of(inventory));
        when(seatInventoryRepository.save(any(SeatInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingSagaRepository.save(any(BookingSaga.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.createOnce(request(), PASSENGER_ID, null);

        assertEquals(BookingStatus.REQUESTED, response.status());
        assertEquals(3, inventory.getAvailableSeats(), "one seat must be held");

        // booking_sagas: running, AUTHORIZE_PAYMENT, deadline now + 15 minutes
        ArgumentCaptor<BookingSaga> sagaCaptor = ArgumentCaptor.forClass(BookingSaga.class);
        verify(bookingSagaRepository).save(sagaCaptor.capture());
        BookingSaga saga = sagaCaptor.getValue();
        assertEquals(response.bookingId(), saga.getBookingId());
        assertEquals(BookingSaga.Status.RUNNING, saga.getStatus());
        assertEquals(SagaStep.AUTHORIZE_PAYMENT, saga.getCurrentStep());
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 15), saga.getDeadlineAt());

        // estimated fare: 12 km from the fake provider, passengers = 4 - 3 = 1 (minimum)
        verify(fareService).recordFare(eq(response.bookingId()), eq(Fare.ESTIMATED),
                eq(LocalDate.of(2026, 10, 25)), eq(new BigDecimal("12")), eq(1));

        // SeatHeld then PaymentAuthorizationRequested
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(events.capture());
        List<Object> published = events.getAllValues();
        assertInstanceOf(SeatHeld.class, published.get(0));
        SeatHeld seatHeld = (SeatHeld) published.get(0);
        assertEquals(response.bookingId(), seatHeld.bookingId());
        assertEquals(GROUP_ID, seatHeld.groupId());
        assertEquals(PASSENGER_ID, seatHeld.passengerId());

        assertInstanceOf(PaymentAuthorizationRequested.class, published.get(1));
        PaymentAuthorizationRequested requested = (PaymentAuthorizationRequested) published.get(1);
        assertEquals(response.bookingId(), requested.bookingId());
        assertEquals(GROUP_ID, requested.groupId());
        assertEquals(PASSENGER_ID, requested.payerId());
        assertEquals(LocalDate.of(2026, 10, 25), requested.date());
        assertEquals(new BigDecimal("12"), requested.distanceKm());
        assertEquals(1, requested.passengers());
    }

    @Test
    void noSeatsLeftRejectsTheTransactionBeforeCreatingAnything() {
        when(seatInventoryRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(SeatInventory.restore(GROUP_ID, 4, 0, 0L, null)));

        assertThrows(NoSeatsAvailableException.class,
                () -> service.createOnce(request(), PASSENGER_ID, null));

        verify(seatInventoryRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(bookingSagaRepository, fareService, eventPublisher);
    }

    @Test
    void unknownGroupIsReportedAsNotFound() {
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        assertThrows(GroupNotFoundException.class, () -> service.createOnce(request(), PASSENGER_ID, null));
        verifyNoInteractions(bookingSagaRepository, fareService, eventPublisher);
    }

    @Test
    void duplicateRequestIdIsRejectedAsConflict() {
        when(bookingRepository.existsByRequestId(REQUEST_ID)).thenReturn(true);
        assertThrows(BookingAlreadyExistsException.class, () -> service.createOnce(request(), PASSENGER_ID, null));
        verify(seatInventoryRepository, never()).save(any());
        verifyNoInteractions(bookingSagaRepository, fareService, eventPublisher);
    }

    // ------------------------------------------------------------------------- cancelOnce

    private Booking requestedBooking() {
        return Booking.create(REQUEST_ID, GROUP_ID, PASSENGER_ID, "idem-key");
    }

    /** Booking REQUESTED, one of 4 seats held, saga running and an estimated fare of 21.34 PEN. */
    private void cancellableBooking(Booking booking, SeatInventory inventory, BookingSaga saga) {
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(seatInventoryRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(inventory));
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));
        when(fareRepository.findByBookingIdAndFareType(booking.getBookingId(), Fare.ESTIMATED))
                .thenReturn(Optional.of(new Fare(UUID.randomUUID(), booking.getBookingId(), 1L,
                        Fare.ESTIMATED, new BigDecimal("12"), 1, new BigDecimal("21.34"), "PEN")));
    }

    @Test
    void cancelFromRequestedReleasesTheSeatCompensatesTheSagaAndPublishesBookingCancelled() {
        Booking booking = requestedBooking();
        SeatInventory inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
        BookingSaga saga = BookingSaga.start(booking.getBookingId(), SagaStep.AUTHORIZE_PAYMENT,
                LocalDateTime.of(2026, 10, 25, 12, 15));
        cancellableBooking(booking, inventory, saga);
        when(departureTimeProvider.departureAt(GROUP_ID))
                .thenReturn(FIXED_CLOCK.instant().plus(java.time.Duration.ofMinutes(120)));

        CancellationResponse response = service.cancelOnce(booking.getBookingId(), PASSENGER_ID);

        assertEquals(BookingStatus.CANCELLED, response.status());
        assertNull(response.penaltyAmount(), "a cancellation 120 min ahead must not be fined");
        assertNull(response.currency());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(4, inventory.getAvailableSeats(), "the held seat must be released (+1)");
        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        BookingCancelled cancelled = (BookingCancelled) events.getValue();
        assertEquals(booking.getBookingId(), cancelled.bookingId());
        assertFalse(cancelled.penaltyApplicable());

        verify(penaltyRepository, never()).save(any());
        verify(bookingRepository).save(booking);
        verify(seatInventoryRepository).save(inventory);
        verify(bookingSagaRepository).save(saga);
    }

    @Test
    void lateCancellationCreatesTheTwentyPercentPenaltyWithAOneSolFloor() {
        Booking booking = requestedBooking();
        SeatInventory inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
        BookingSaga saga = BookingSaga.start(booking.getBookingId(), SagaStep.WAIT_GROUP,
                LocalDateTime.of(2026, 10, 25, 13, 0));
        cancellableBooking(booking, inventory, saga);
        when(departureTimeProvider.departureAt(GROUP_ID))
                .thenReturn(FIXED_CLOCK.instant().plus(java.time.Duration.ofMinutes(10)));

        CancellationResponse response = service.cancelOnce(booking.getBookingId(), PASSENGER_ID);

        // 20 % of the estimated 21.34 = 4.268 -> 4.27 (HALF_UP), above the 1.00 floor
        assertEquals(0, new BigDecimal("4.27").compareTo(response.penaltyAmount()));
        assertEquals("PEN", response.currency());

        ArgumentCaptor<Penalty> penalty = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyRepository).save(penalty.capture());
        assertEquals(booking.getBookingId(), penalty.getValue().bookingId());
        assertEquals(Penalty.LATE_CANCELLATION, penalty.getValue().reason());
        assertEquals(0, new BigDecimal("4.27").compareTo(penalty.getValue().amount()));

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        assertTrue(((BookingCancelled) events.getValue()).penaltyApplicable());
    }

    @Test
    void cancellingTwiceReleasesTheSeatOnlyOnceAndNeverChargesTwice() {
        Booking booking = requestedBooking();
        SeatInventory inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
        BookingSaga saga = BookingSaga.start(booking.getBookingId(), SagaStep.WAIT_GROUP,
                LocalDateTime.of(2026, 10, 25, 13, 0));
        cancellableBooking(booking, inventory, saga);
        when(departureTimeProvider.departureAt(GROUP_ID))
                .thenReturn(FIXED_CLOCK.instant().plus(java.time.Duration.ofMinutes(10)));

        CancellationResponse first = service.cancelOnce(booking.getBookingId(), PASSENGER_ID);
        CancellationResponse second = service.cancelOnce(booking.getBookingId(), PASSENGER_ID);

        assertEquals(BookingStatus.CANCELLED, first.status());
        assertNotNull(first.penaltyAmount());
        assertEquals(BookingStatus.CANCELLED, second.status());
        assertNull(second.penaltyAmount(), "the idempotent answer must not charge a second penalty");

        assertEquals(4, inventory.getAvailableSeats(), "available_seats must not go above capacity");
        verify(seatInventoryRepository, times(1)).save(any());
        verify(penaltyRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    void cancellingABoardedBookingIsAConflictThatTouchesNothing() {
        Booking booking = Booking.restore(UUID.randomUUID(), REQUEST_ID, GROUP_ID, PASSENGER_ID, "idem-key",
                BookingStatus.BOARDED, null, null, 1L, null);
        SeatInventory inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
        cancellableBooking(booking, inventory,
                BookingSaga.restore(UUID.randomUUID(), booking.getBookingId(), SagaStep.SEARCH_DRIVER,
                        BookingSaga.Status.COMPLETED, null, null));

        assertThrows(BookingCannotBeCancelledException.class,
                () -> service.cancelOnce(booking.getBookingId(), PASSENGER_ID));

        assertEquals(BookingStatus.BOARDED, booking.getStatus());
        assertEquals(3, inventory.getAvailableSeats());
        verify(bookingRepository, never()).save(any());
        verify(seatInventoryRepository, never()).save(any());
        verifyNoInteractions(penaltyRepository, eventPublisher);
    }

    @Test
    void cancellingAnUnknownOrForeignBookingIsNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> service.cancelOnce(bookingId, PASSENGER_ID));

        Booking booking = requestedBooking();
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        assertThrows(BookingNotFoundException.class,
                () -> service.cancelOnce(booking.getBookingId(), UUID.randomUUID()));
        assertEquals(BookingStatus.REQUESTED, booking.getStatus());
    }
}
