package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.handler.PaymentAuthorizationFailedHandler;
import com.fundatechperu.shareway.booking.application.handler.PaymentAuthorizedHandler;
import com.fundatechperu.shareway.booking.application.port.IntegrationOutbox;
import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.domain.service.DepartureTimeProvider;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Saga handlers: PaymentAuthorized -> authorization recorded and WAIT_GROUP (still running),
 * PaymentAuthorizationFailed -> full compensation (booking cancelled, seat +1, saga
 * compensated, BookingCancelled). Unknown bookings are ignored instead of failing, because
 * these events arrive from the outbox relay.
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
    private final IntegrationOutbox integrationOutbox = mock(IntegrationOutbox.class);
    private final DepartureTimeProvider departureTimeProvider = mock(DepartureTimeProvider.class);
    private final BookingCancellationService cancellationService = new BookingCancellationService(
            bookingRepository, seatInventoryRepository, bookingSagaRepository, eventPublisher,
            integrationOutbox, departureTimeProvider, FIXED_CLOCK);

    private Booking booking;
    private BookingSaga saga;
    private SeatInventory inventory;

    @BeforeEach
    void setUp() {
        booking = Booking.create(UUID.randomUUID(), GROUP_ID, PASSENGER_ID, null);
        saga = BookingSaga.start(booking.getBookingId(), SagaStep.AUTHORIZE_PAYMENT,
                LocalDateTime.of(2026, 10, 25, 12, 15));
        // 4 seats, 1 held by the booking under test
        inventory = SeatInventory.restore(GROUP_ID, 4, 3, 1L, null);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(bookingSagaRepository.findByBookingId(booking.getBookingId())).thenReturn(Optional.of(saga));
        when(seatInventoryRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(inventory));
        when(departureTimeProvider.departureAt(GROUP_ID)).thenReturn(FIXED_CLOCK.instant().plusSeconds(7200));
    }

    private PaymentAuthorizedHandler authorizedHandler() {
        return new PaymentAuthorizedHandler(bookingSagaRepository, bookingRepository, cancellationService,
                FIXED_CLOCK, 60L);
    }

    private PaymentAuthorizationFailedHandler failedHandler() {
        return new PaymentAuthorizationFailedHandler(bookingRepository, bookingSagaRepository, cancellationService);
    }

    // ---------------------------------------------------------------- PaymentAuthorized

    @Test
    void paymentAuthorizedRecordsThePaymentAndAdvancesTheSagaToWaitGroup() {
        UUID paymentId = UUID.randomUUID();
        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), paymentId);
        PaymentAuthorizedHandler handler = authorizedHandler();
        assertTrue(handler.supports(event));
        handler.onEvent(event);

        assertTrue(booking.isPaymentAuthorized());
        assertEquals(paymentId, booking.getPaymentId());
        assertEquals(SagaStep.WAIT_GROUP, saga.getCurrentStep());
        assertEquals(BookingSaga.Status.RUNNING, saga.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 25, 13, 0), saga.getDeadlineAt(),
                "the group deadline must be now + 60 minutes");
        verify(bookingSagaRepository).save(saga);
        verifyNoInteractions(integrationOutbox);
    }

    @Test
    void paymentAuthorizedForUnknownBookingIsIgnored() {
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.empty());

        authorizedHandler().onEvent(new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID()));

        verify(bookingSagaRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void paymentAuthorizedForACompensatedSagaDoesNotMoveIt() {
        saga.compensate();

        authorizedHandler().onEvent(new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID()));

        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());
        assertEquals(SagaStep.AUTHORIZE_PAYMENT, saga.getCurrentStep());
        verify(bookingSagaRepository, never()).save(any());
    }

    /** Cancelled while the authorization was in flight: Payments must release the money. */
    @Test
    void paymentAuthorizedForABookingCancelledMeanwhileAsksPaymentsToReleaseIt() {
        booking.cancel(CancellationReason.PASSENGER_CANCELLED, LocalDateTime.of(2026, 10, 25, 11, 59));
        saga.compensate();

        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), UUID.randomUUID());
        authorizedHandler().onEvent(event);
        authorizedHandler().onEvent(event);   // a second delivery asks nothing again

        verify(integrationOutbox, times(1)).bookingCancelled(eq(booking.getBookingId()),
                eq(CancellationReason.PASSENGER_CANCELLED), eq(Instant.parse("2026-10-25T11:59:00Z")), any());
    }

    // ------------------------------------------------------- PaymentAuthorizationFailed

    @Test
    void failedPaymentCompensatesBookingSeatSagaAndPublishesBookingCancelled() {
        PaymentAuthorizationFailed event = new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), "card declined");
        PaymentAuthorizationFailedHandler handler = failedHandler();
        assertTrue(handler.supports(event));
        handler.onEvent(event);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(CancellationReason.PAYMENT_FAILED, booking.getCancellationReason());
        assertEquals(4, inventory.getAvailableSeats(), "the held seat must be released (+1)");
        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(events.capture());
        BookingCancelled cancelled = (BookingCancelled) events.getValue();
        assertEquals(booking.getBookingId(), cancelled.bookingId());
        assertEquals(GROUP_ID, cancelled.groupId());
        assertEquals(PASSENGER_ID, cancelled.payerId());
        assertFalse(cancelled.penaltyApplicable());
        assertEquals(CancellationReason.PAYMENT_FAILED, cancelled.reason());

        verify(bookingRepository).save(booking);
        verify(seatInventoryRepository).save(inventory);
        verify(bookingSagaRepository).save(saga);
        verifyNoInteractions(integrationOutbox);   // nothing was authorized: nothing to release
    }

    @Test
    void redeliveringTheFailedEventDoesNotReleaseASecondSeat() {
        PaymentAuthorizationFailed event = new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), "card declined");

        failedHandler().onEvent(event);
        failedHandler().onEvent(event);

        assertEquals(4, inventory.getAvailableSeats());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(BookingSaga.Status.COMPENSATED, saga.getStatus());
        verify(eventPublisher, times(1)).publish(any());
        verify(seatInventoryRepository, times(1)).save(any());
    }

    @Test
    void failedPaymentForUnknownBookingIsIgnored() {
        when(bookingRepository.findById(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> failedHandler().onEvent(new PaymentAuthorizationFailed(UUID.randomUUID(),
                FIXED_CLOCK.instant(), booking.getBookingId(), "card declined")));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void failedPaymentOnACompletedBookingLeavesItUntouched() {
        Booking completed = Booking.restore(booking.getBookingId(), booking.getRequestId(), GROUP_ID,
                PASSENGER_ID, null, BookingStatus.COMPLETED, null, null, 1L, null);
        when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(completed));
        saga.complete();

        failedHandler().onEvent(new PaymentAuthorizationFailed(UUID.randomUUID(), FIXED_CLOCK.instant(),
                booking.getBookingId(), "too late"));

        assertEquals(BookingStatus.COMPLETED, completed.getStatus());
        assertEquals(3, inventory.getAvailableSeats());
        assertEquals(BookingSaga.Status.COMPLETED, saga.getStatus());
        verifyNoInteractions(eventPublisher);
    }
}
