package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * PaymentAuthorizationFailed (published by the outbox relay when Payments answers 402, 404 or
 * 422): compensating transaction, all in one local transaction: booking -> cancelled
 * (PAYMENT_FAILED), one seat released, saga -> compensated and BookingCancelled published.
 * Re-delivery is a no-op (already cancelled).
 */
@Service
public class PaymentAuthorizationFailedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationFailedHandler.class);

    private final BookingRepository bookingRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final BookingCancellationService cancellationService;

    public PaymentAuthorizationFailedHandler(BookingRepository bookingRepository,
                                             BookingSagaRepository bookingSagaRepository,
                                             BookingCancellationService cancellationService) {
        this.bookingRepository = bookingRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.cancellationService = cancellationService;
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
        Optional<Booking> found = bookingRepository.findById(bookingId);
        if (found.isEmpty()) {
            log.warn("PaymentAuthorizationFailed for unknown booking {}; ignored.", bookingId);
            return;
        }
        Booking booking = found.get();
        log.info("Payment authorization of booking {} failed: {}", bookingId, failed.reason());
        if (booking.isCancellable()) {
            cancellationService.cancelBySystem(booking, CancellationReason.PAYMENT_FAILED);
            return;
        }
        // Already cancelled (or further): only make sure the saga ends compensated.
        bookingSagaRepository.findByBookingId(bookingId).ifPresent(saga -> {
            if (saga.getStatus() == BookingSaga.Status.RUNNING) {
                saga.compensate();
                bookingSagaRepository.save(saga);
            }
        });
    }
}
