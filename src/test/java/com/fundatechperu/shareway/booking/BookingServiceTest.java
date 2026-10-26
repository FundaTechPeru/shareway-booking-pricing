package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.booking.application.dto.response.CancellationResponse;
import com.fundatechperu.shareway.booking.application.service.BookingService;
import com.fundatechperu.shareway.booking.application.service.BookingTransactionalService;
import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.booking.domain.exception.NoSeatsAvailableException;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * POST /bookings behaviour outside the transaction: validation (400), optimistic-lock
 * retries, and the 409 answer "El viaje ya no tiene asientos disponibles.".
 */
class BookingServiceTest {

    private static final String NO_SEATS_MESSAGE = "El viaje ya no tiene asientos disponibles.";
    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PASSENGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BookingTransactionalService transactionalService = mock(BookingTransactionalService.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(transactionalService, bookingRepository, 3, 1);
    }

    private CreateBookingRequest request() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setRequestId(UUID.randomUUID());
        request.setGroupId(GROUP_ID);
        return request;
    }

    @Test
    void retriesOptimisticLockFailuresUntilTheTransactionSucceeds() {
        BookingResponse accepted = new BookingResponse(UUID.randomUUID(), BookingStatus.REQUESTED);
        when(transactionalService.createOnce(any(), eq(PASSENGER_ID), eq("idem-key")))
                .thenThrow(new ObjectOptimisticLockingFailureException("seat_inventories", GROUP_ID))
                .thenThrow(new CannotAcquireLockException("lock wait timeout"))
                .thenReturn(accepted);

        BookingResponse response = service.createBooking(request(), PASSENGER_ID, "idem-key");

        assertEquals(accepted, response);
        verify(transactionalService, times(3)).createOnce(any(), eq(PASSENGER_ID), eq("idem-key"));
        verify(transactionalService, times(2)).incrementRetry();
    }

    @Test
    void optimisticLockExhaustingRetriesAnswersTheNoSeatsConflictMessage() {
        when(transactionalService.createOnce(any(), eq(PASSENGER_ID), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("seat_inventories", GROUP_ID));

        NoSeatsAvailableException exception = assertThrows(NoSeatsAvailableException.class,
                () -> service.createBooking(request(), PASSENGER_ID, null));

        assertEquals(NO_SEATS_MESSAGE, exception.getMessage());
        verify(transactionalService, times(3)).createOnce(any(), eq(PASSENGER_ID), any());
        verify(transactionalService, times(3)).incrementRetry();
    }

    @Test
    void noSeatsLeftKeepsTheSameConflictMessage() {
        when(transactionalService.createOnce(any(), eq(PASSENGER_ID), any()))
                .thenThrow(new NoSeatsAvailableException());

        NoSeatsAvailableException exception = assertThrows(NoSeatsAvailableException.class,
                () -> service.createBooking(request(), PASSENGER_ID, null));

        assertEquals(NO_SEATS_MESSAGE, exception.getMessage());
        verify(transactionalService, times(1)).createOnce(any(), eq(PASSENGER_ID), any());
    }

    @Test
    void missingGroupIdOrRequestIdIsABadRequest() {
        CreateBookingRequest empty = new CreateBookingRequest();
        assertThrows(InvalidBookingRequestException.class,
                () -> service.createBooking(empty, PASSENGER_ID, null));
        assertThrows(InvalidBookingRequestException.class,
                () -> service.createBooking(null, PASSENGER_ID, null));
        verifyNoInteractions(transactionalService);
    }

    @Test
    void missingPassengerHeaderIsABadRequest() {
        assertThrows(InvalidBookingRequestException.class,
                () -> service.createBooking(request(), null, null));
        verifyNoInteractions(transactionalService);
    }

    @Test
    void unknownBookingIsReportedAsNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        BookingNotFoundException exception = assertThrows(BookingNotFoundException.class,
                () -> service.findById(bookingId));
        assertTrue(exception.getMessage().contains(bookingId.toString()));
    }

    // ------------------------------------------------------------------- cancel (X-User-Id)

    @Test
    void cancelDelegatesToTheCancellationTransactionAndReturnsItsAnswer() {
        CancellationResponse response = new CancellationResponse(
                UUID.randomUUID(), BookingStatus.CANCELLED, null, null);
        when(transactionalService.cancelOnce(any(), eq(PASSENGER_ID))).thenReturn(response);

        assertSame(response, service.cancel(response.bookingId(), PASSENGER_ID));

        verify(transactionalService).cancelOnce(response.bookingId(), PASSENGER_ID);
        verify(transactionalService, never()).incrementRetry();
    }

    @Test
    void cancelRetriesOptimisticLockFailuresUntilTheTransactionSucceeds() {
        CancellationResponse response = new CancellationResponse(
                UUID.randomUUID(), BookingStatus.CANCELLED, null, null);
        when(transactionalService.cancelOnce(any(), eq(PASSENGER_ID)))
                .thenThrow(new ObjectOptimisticLockingFailureException("seat_inventories", GROUP_ID))
                .thenReturn(response);

        assertSame(response, service.cancel(response.bookingId(), PASSENGER_ID));

        verify(transactionalService, times(2)).cancelOnce(any(), eq(PASSENGER_ID));
        verify(transactionalService, times(1)).incrementRetry();
    }

    @Test
    void cancelRethrowsTheOptimisticFailureAfterExhaustingTheRetries() {
        when(transactionalService.cancelOnce(any(), eq(PASSENGER_ID)))
                .thenThrow(new ObjectOptimisticLockingFailureException("seat_inventories", GROUP_ID));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.cancel(UUID.randomUUID(), PASSENGER_ID));

        verify(transactionalService, times(3)).cancelOnce(any(), eq(PASSENGER_ID));
        verify(transactionalService, times(3)).incrementRetry();
    }

    @Test
    void cancelFromAnotherPassengerIsNotFoundAndIsNotRetried() {
        UUID bookingId = UUID.randomUUID();
        when(transactionalService.cancelOnce(eq(bookingId), any()))
                .thenThrow(new BookingNotFoundException(bookingId));

        assertThrows(BookingNotFoundException.class, () -> service.cancel(bookingId, UUID.randomUUID()));

        verify(transactionalService, times(1)).cancelOnce(eq(bookingId), any());
        verify(transactionalService, never()).incrementRetry();
    }

    @Test
    void cancelWithoutXUserHeaderIsABadRequest() {
        assertThrows(InvalidBookingRequestException.class,
                () -> service.cancel(UUID.randomUUID(), null));
        verifyNoInteractions(transactionalService);
    }
}
