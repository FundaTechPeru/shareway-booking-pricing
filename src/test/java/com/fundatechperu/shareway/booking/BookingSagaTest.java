package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingSagaTest {

    @Test
    void startCreatesARunningSaga() {
        UUID bookingId = UUID.randomUUID();
        BookingSaga saga = BookingSaga.start(bookingId, "payment_requested",
                LocalDateTime.now().plusMinutes(10));
        assertEquals(BookingSaga.Status.RUNNING, saga.getStatus());
        assertEquals(bookingId, saga.getBookingId());
        assertEquals("payment_requested", saga.getCurrentStep());
    }

    @Test
    void advanceKeepsTheSagaRunning() {
        BookingSaga saga = BookingSaga.start(UUID.randomUUID(), "payment_requested", null);
        saga.advance("payment_authorized");
        assertEquals(BookingSaga.Status.RUNNING, saga.getStatus());
        assertEquals("payment_authorized", saga.getCurrentStep());
    }

    @Test
    void sagaCanBeCompletedOrCompensated() {
        BookingSaga completed = BookingSaga.start(UUID.randomUUID(), "step", null);
        completed.complete();
        assertEquals(BookingSaga.Status.COMPLETED, completed.getStatus());

        BookingSaga compensated = BookingSaga.start(UUID.randomUUID(), "step", null);
        compensated.compensate();
        assertEquals(BookingSaga.Status.COMPENSATED, compensated.getStatus());
    }

    @Test
    void finishedSagaCannotAdvance() {
        BookingSaga saga = BookingSaga.start(UUID.randomUUID(), "step", null);
        saga.complete();
        assertThrows(IllegalStateException.class, () -> saga.advance("another_step"));
        assertEquals(BookingSaga.Status.COMPLETED, saga.getStatus());
    }

    @Test
    void restoreKeepsPersistedState() {
        BookingSaga restored = BookingSaga.restore(UUID.randomUUID(), UUID.randomUUID(), "payment_authorized",
                BookingSaga.Status.COMPENSATED, null, LocalDateTime.now());
        assertEquals(BookingSaga.Status.COMPENSATED, restored.getStatus());
        assertEquals("payment_authorized", restored.getCurrentStep());
    }
}
