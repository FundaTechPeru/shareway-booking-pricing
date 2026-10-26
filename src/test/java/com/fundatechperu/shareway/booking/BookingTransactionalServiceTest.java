package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.service.BookingTransactionalService;
import com.fundatechperu.shareway.booking.domain.exception.BookingAlreadyExistsException;
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
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.infrastructure.provider.FakeGroupInfoProvider;
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
    private final EventPublisher eventPublisher = mock(EventPublisher.class);

    private BookingTransactionalService service;

    @BeforeEach
    void setUp() {
        service = new BookingTransactionalService(bookingRepository, seatInventoryRepository,
                bookingSagaRepository, fareService, new FakeGroupInfoProvider(), eventPublisher,
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
}
