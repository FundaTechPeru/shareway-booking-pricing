package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.exception.GroupNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * PaymentAuthorizationFailed: compensating transaction, all in one local transaction:
 * booking -> cancelled, one seat released (available_seats + 1), saga -> compensated and
 * BookingCancelled published. Re-delivery of the event is a no-op (already cancelled).
 */
@Service
public class PaymentAuthorizationFailedHandler implements EventHandler {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * The EventPublisher is injected lazily because it (as InMemoryEventPublisher) depends on
     * all EventHandlers, including this one; without @Lazy the two beans would form a cycle.
     */
    public PaymentAuthorizationFailedHandler(BookingRepository bookingRepository,
                                             SeatInventoryRepository seatInventoryRepository,
                                             BookingSagaRepository bookingSagaRepository,
                                             @Lazy EventPublisher eventPublisher,
                                             Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PaymentAuthorizationFailed;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        PaymentAuthorizationFailed failed = (PaymentAuthorizationFailed) event;
        UUID bookingId = failed.bookingId();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingSaga saga = bookingSagaRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        SeatInventory inventory = seatInventoryRepository.findById(booking.getGroupId())
                .orElseThrow(() -> new GroupNotFoundException(booking.getGroupId()));

        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.cancel();                    // requested|grouped|confirmed -> cancelled
            bookingRepository.save(booking);
            inventory.releaseSeat();             // available_seats + 1
            seatInventoryRepository.save(inventory);
            eventPublisher.publish(new BookingCancelled(UUID.randomUUID(), clock.instant(),
                    bookingId, booking.getGroupId(), booking.getPassengerId(), false));
        }

        if (saga.getStatus() != BookingSaga.Status.COMPENSATED) {
            saga.compensate();
            bookingSagaRepository.save(saga);
        }
    }
}
