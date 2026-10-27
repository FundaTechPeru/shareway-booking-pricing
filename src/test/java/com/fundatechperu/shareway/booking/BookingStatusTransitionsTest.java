package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.exception.InvalidBookingTransitionException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State machine:
 * requested -> grouped -> confirmed -> boarded -> in_progress -> completed,
 * and requested|grouped|confirmed -> cancelled. Every other transition is invalid.
 */
class BookingStatusTransitionsTest {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            BookingStatus.REQUESTED, Set.of(BookingStatus.GROUPED, BookingStatus.CANCELLED),
            BookingStatus.GROUPED, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.BOARDED, BookingStatus.CANCELLED),
            BookingStatus.BOARDED, Set.of(BookingStatus.IN_PROGRESS),
            BookingStatus.IN_PROGRESS, Set.of(BookingStatus.COMPLETED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of()
    );

    @Test
    void everyTransitionFollowsTheSpec() {
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                Booking booking = bookingIn(from);
                if (ALLOWED.get(from).contains(to)) {
                    assertDoesNotThrow(() -> booking.transitionTo(to), from + " -> " + to + " must be allowed");
                    assertEquals(to, booking.getStatus());
                } else {
                    assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(to),
                            from + " -> " + to + " must be rejected");
                    assertEquals(from, booking.getStatus(),
                            "status must not change when the transition is rejected");
                }
            }
        }
    }

    @Test
    void happyPathReachesCompleted() {
        Booking booking = Booking.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        booking.group();
        booking.confirm();
        booking.markBoarded();
        booking.start();
        booking.complete();
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
    }

    @Test
    void cancellationIsAllowedFromRequestedGroupedAndConfirmed() {
        for (BookingStatus from : Set.of(BookingStatus.REQUESTED, BookingStatus.GROUPED, BookingStatus.CONFIRMED)) {
            Booking booking = bookingIn(from);
            booking.cancel();
            assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        }
    }

    @Test
    void cancellationIsRejectedFromBoardedInProgressCompletedAndCancelled() {
        for (BookingStatus from : Set.of(BookingStatus.BOARDED, BookingStatus.IN_PROGRESS,
                BookingStatus.COMPLETED, BookingStatus.CANCELLED)) {
            Booking booking = bookingIn(from);
            assertThrows(InvalidBookingTransitionException.class, booking::cancel,
                    "cancel from " + from + " must be rejected");
            assertEquals(from, booking.getStatus());
        }
    }

    @Test
    void requestedCannotSkipGrouping() {
        Booking booking = bookingIn(BookingStatus.REQUESTED);
        assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(BookingStatus.CONFIRMED));
        assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(BookingStatus.BOARDED));
        assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(BookingStatus.COMPLETED));
        assertEquals(BookingStatus.REQUESTED, booking.getStatus());
    }

    @Test
    void sameStatusTransitionIsRejected() {
        for (BookingStatus status : BookingStatus.values()) {
            Booking booking = bookingIn(status);
            assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(status),
                    status + " -> " + status + " must be rejected");
        }
    }

    @Test
    void nullTargetIsRejected() {
        Booking booking = bookingIn(BookingStatus.REQUESTED);
        assertThrows(InvalidBookingTransitionException.class, () -> booking.transitionTo(null));
    }

    private Booking bookingIn(BookingStatus status) {
        return Booking.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, status, null, null, 0L, LocalDateTime.now());
    }
}
