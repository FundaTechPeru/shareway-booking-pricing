package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.handler.PaymentAuthorizationFailedHandler;
import com.fundatechperu.shareway.booking.application.handler.PaymentAuthorizedHandler;
import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Saga handlers: PaymentAuthorized -> WAIT_GROUP (still running), PaymentAuthorizationFailed
 * -> full compensation (booking cancelled, seat +1, saga compensated, BookingCancelled).
 */
class BookingSagaHandlersTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PASSENGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final SeatInventoryRepository seatInventoryRepository = mock(SeatInventoryRepository.class);
    private final BookingSagaRepository bookingSagaRepository = mock(BookingSagaRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);

    private Booking booking;
    private BookingSaga saga;
    private SeatInventory inventory;

    @BeforeEach
    void setUp() {
        booking = Booking.create(UUID.randomUUID(), GROUP_ID, PASSENGER_ID, null);
        saga = BookingSaga.start(booking.getBookingId(), SagaStep.AUTHORIZE_PAYMENT,
                java.time.LocalDateTime.of(2026, 10, 25, 12, 15));
        // 4 seats, 1 held by the booking under test
        inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
    }

    // ---------------------------------------------------------------- PaymentAuthorized

    @Test
    void paymentAuthorizedAdvancesTheSagaToWaitGroupAndKeepsItRunning() {
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));

        PaymentAuthorizedHandler handler = new PaymentAuthorizedHandler(bookingSagaRepository);
        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID());
        assertTrue(handler.supports(event));
        handler.onEvent(event);

        assertEquals(SagaStep.WAIT_GROUP, saga.getCurrentStep());
        assertEquals(BookingSaga.Status.RUNNING, saga.getStatus());
        verify(bookingSagaRepository).save(saga);
    }

    @Test
    void paymentAuthorizedForUnknownBookingIsNotFound() {
        when(bookingSagaRepository.findByBookingId(any())).thenReturn(Optional.empty());

        PaymentAuthorizedHandler handler = new PaymentAuthorizedHandler(bookingSagaRepository);
        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID());

        assertThrows(BookingNotFoundException.class, () -> handler.onEvent(event));
        verify(bookingSagaRepository, never()).save(any());
    }

    @Test
    void paymentAuthorizedForACompensatedSagaIsIgnored() {
        saga.compensate();
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));

        PaymentAuthorizedHandler handler = new PaymentAuthorizedHandler(bookingSagaRepository);
        handler.onEvent(new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID()));

        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());
        assertEquals(SagaStep.AUTHORIZE_PAYMENT, saga.getCurrentStep());
        verify(bookingSagaRepository, never()).save(any());
    }

    // ------------------------------------------------------- PaymentAuthorizationFailed

    @Test
    void failedPaymentCompensatesBookingSeatSagaAndPublishesBookingCancelled() {
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.of(inventory));

        PaymentAuthorizationFailedHandler handler = new PaymentAuthorizationFailedHandler(
                bookingRepository, seatInventoryRepository, bookingSagaRepository, eventPublisher, FIXED_CLOCK);
        PaymentAuthorizationFailed event = new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), "card declined");
        assertTrue(handler.supports(event));
        handler.onEvent(event);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(4, inventory.getAvailableSeats(), "the held seat must be released (+1)");
        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        BookingCancelled cancelled = (BookingCancelled) events.getValue();
        assertEquals(booking.getBookingId(), cancelled.bookingId());
        assertEquals(GROUP_ID, cancelled.groupId());
        assertEquals(PASSENGER_ID, cancelled.payerId());
        assertFalse(cancelled.penaltyApplicable());

        verify(bookingRepository).save(booking);
        verify(seatInventoryRepository).save(inventory);
        verify(bookingSagaRepository).save(saga);
    }

    @Test
    void redeliveringTheFailedEventDoesNotReleaseASecondSeat() {
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.of(inventory));

        PaymentAuthorizationFailedHandler handler = new PaymentAuthorizationFailedHandler(
                bookingRepository, seatInventoryRepository, bookingSagaRepository, eventPublisher, FIXED_CLOCK);
        PaymentAuthorizationFailed event = new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), "card declined");

        handler.onEvent(event);
        handler.onEvent(event);

        assertEquals(4, inventory.getAvailableSeats());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());
        verify(eventPublisher, times(1)).publish(any());
        verify(seatInventoryRepository, times(1)).save(any());
    }

    @Test
    void failedPaymentForUnknownBookingIsNotFound() {
        when(bookingRepository.findById(any())).thenReturn(Optional.empty());

        PaymentAuthorizationFailedHandler handler = new PaymentAuthorizationFailedHandler(
                bookingRepository, seatInventoryRepository, bookingSagaRepository, eventPublisher, FIXED_CLOCK);

        assertThrows(BookingNotFoundException.class, () -> handler.onEvent(
                new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                        booking.getBookingId(), "card declined")));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void failedPaymentOnACompletedBookingIsRejectedAsConflict() {
        Booking completed = Booking.restore(booking.getBookingId(), booking.getRequestId(), GROUP_ID,
                PASSENGER_ID, null, BookingStatus.COMPLETED, null, null, 1L, null);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(completed));
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.of(inventory));

        PaymentAuthorizationFailedHandler handler = new PaymentAuthorizationFailedHandler(
                bookingRepository, seatInventoryRepository, bookingSagaRepository, eventPublisher, FIXED_CLOCK);

        assertThrows(com.fundatechperu.shareway.booking.domain.exception.InvalidBookingTransitionException.class,
                () -> handler.onEvent(new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                        booking.getBookingId(), "too late")));
        assertEquals(3, inventory.getAvailableSeats());
        verifyNoInteractions(eventPublisher);
    }
}
