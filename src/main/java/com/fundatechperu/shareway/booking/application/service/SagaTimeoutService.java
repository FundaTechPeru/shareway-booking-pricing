package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compensation of expired booking sagas: every saga still RUNNING whose deadline_at has
 * passed is compensated — the booking becomes cancelled, one seat is released and
 * BookingCancelled is published (see {@link BookingCancellationService}). The reason depends
 * on the step where the saga stalled: PAYMENT_TIMEOUT while waiting for the payment
 * authorization and NO_DRIVER_AVAILABLE while waiting for the group or a driver; in the
 * latter case the payment was authorized, so Payments is told to void it.
 * Invoked every minute by SagaTimeoutJob; calling it twice is harmless (nothing matches
 * twice) so it is also safe to invoke it manually.
 */
@Service
public class SagaTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutService.class);

    private final BookingSagaRepository bookingSagaRepository;
    private final BookingRepository bookingRepository;
    private final BookingCancellationService cancellationService;
    private final Clock clock;

    public SagaTimeoutService(BookingSagaRepository bookingSagaRepository,
                              BookingRepository bookingRepository,
                              BookingCancellationService cancellationService,
                              Clock clock) {
        this.bookingSagaRepository = bookingSagaRepository;
        this.bookingRepository = bookingRepository;
        this.cancellationService = cancellationService;
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
        CancellationReason reason = SagaStep.AUTHORIZE_PAYMENT.equals(saga.getCurrentStep())
                ? CancellationReason.PAYMENT_TIMEOUT : CancellationReason.NO_DRIVER_AVAILABLE;

        Booking booking = bookingRepository.findById(saga.getBookingId()).orElse(null);
        if (booking != null && booking.isCancellable()) {
            cancellationService.cancelBySystem(booking, reason);   // also compensates the saga
            log.info("Expired saga {} compensated: booking {} cancelled ({}).",
                    saga.getSagaId(), booking.getBookingId(), reason);
            return;
        }
        log.warn("Expired saga {}: booking {} is not cancellable; compensating the saga only ({}).",
                saga.getSagaId(), saga.getBookingId(), reason);
        saga.compensate();
        bookingSagaRepository.save(saga);
    }
}
