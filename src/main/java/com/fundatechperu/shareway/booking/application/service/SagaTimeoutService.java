package com.fundatechperu.shareway.booking.application.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Compensation of expired booking sagas (Sprint 2): every saga still RUNNING whose
 * deadline_at has passed is compensated — the booking becomes cancelled, one seat is
 * released and BookingCancelled is published. The reason depends on the step where the
 * saga stalled: PAYMENT_TIMEOUT while waiting for the payment authorization and
 * NO_DRIVER_AVAILABLE while waiting for the group or a driver.
 * Invoked every minute by SagaTimeoutJob; calling it twice is harmless (nothing matches
 * twice) so it is also safe to invoke it manually.
 */
@Service
public class SagaTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutService.class);

    private final BookingSagaRepository bookingSagaRepository;
    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public SagaTimeoutService(BookingSagaRepository bookingSagaRepository,
                              BookingRepository bookingRepository,
                              SeatInventoryRepository seatInventoryRepository,
                              EventPublisher eventPublisher,
                              Clock clock) {
        this.bookingSagaRepository = bookingSagaRepository;
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** @return how many expired sagas were compensated in this run. */
    @Transactional
    public int compensateExpiredSagas() {
        List<BookingSaga> expired = bookingSagaRepository.findByStatusAndDeadlineAtBefore(
                BookingSaga.Status.RUNNING, LocalDateTime.now(clock));
        for (BookingSaga saga : expired) {
            compensate(saga);
        }
        return expired.size();
    }

    private void compensate(BookingSaga saga) {
        String reason = SagaStep.AUTHORIZE_PAYMENT.equals(saga.getCurrentStep())
                ? "PAYMENT_TIMEOUT" : "NO_DRIVER_AVAILABLE";

        Booking booking = bookingRepository.findById(saga.getBookingId()).orElse(null);
        if (booking != null && isCancellable(booking.getStatus())) {
            booking.cancel();
            bookingRepository.save(booking);
            SeatInventory inventory = seatInventoryRepository.findByIdForUpdate(booking.getGroupId()).orElse(null);
            if (inventory != null) {
                inventory.releaseSeat();
                seatInventoryRepository.save(inventory);
            }
            eventPublisher.publish(new BookingCancelled(UUID.randomUUID(), clock.instant(),
                    booking.getBookingId(), booking.getGroupId(), booking.getPassengerId(), false));
            log.info("Expired saga {} compensated: booking {} cancelled ({}).",
                    saga.getSagaId(), booking.getBookingId(), reason);
        } else {
            log.warn("Expired saga {}: booking {} is not cancellable; compensating the saga only ({}).",
                    saga.getSagaId(), saga.getBookingId(), reason);
        }
        saga.compensate();
        bookingSagaRepository.save(saga);
    }

    private boolean isCancellable(BookingStatus status) {
        return status == BookingStatus.REQUESTED || status == BookingStatus.GROUPED
                || status == BookingStatus.CONFIRMED;
    }
}
