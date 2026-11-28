package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PaymentAuthorized (published by the outbox relay when Payments answers 200/201): the
 * authorization is recorded on the booking and the saga advances from AUTHORIZE_PAYMENT to
 * WAIT_GROUP with the group deadline (+group timeout). Events for a saga that already finished
 * are ignored, except one case: the booking was cancelled while the authorization was in
 * flight, so Payments now holds an authorization for a cancelled booking and is told to void
 * it (booking-cancelled message).
 */
@Service
public class PaymentAuthorizedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizedHandler.class);

    private final BookingSagaRepository bookingSagaRepository;
    private final BookingRepository bookingRepository;
    private final BookingCancellationService cancellationService;
    private final Clock clock;
    private final long groupTimeoutMinutes;

    public PaymentAuthorizedHandler(BookingSagaRepository bookingSagaRepository,
                                    BookingRepository bookingRepository,
                                    BookingCancellationService cancellationService,
                                    Clock clock,
                                    @Value("${booking.saga.group-timeout-minutes:60}") long groupTimeoutMinutes) {
        this.bookingSagaRepository = bookingSagaRepository;
        this.bookingRepository = bookingRepository;
        this.cancellationService = cancellationService;
        this.clock = clock;
        this.groupTimeoutMinutes = groupTimeoutMinutes;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof PaymentAuthorized;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        PaymentAuthorized paymentAuthorized = (PaymentAuthorized) event;
        Optional<Booking> found = bookingRepository.findById(paymentAuthorized.bookingId());
        if (found.isEmpty()) {
            log.warn("PaymentAuthorized for unknown booking {}; ignored.", paymentAuthorized.bookingId());
            return;
        }
        Booking booking = found.get();
        boolean firstAuthorization = !booking.isPaymentAuthorized();
        booking.recordPaymentAuthorization(paymentAuthorized.paymentId(), LocalDateTime.now(clock));
        bookingRepository.save(booking);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            if (firstAuthorization) {
                log.info("Booking {} was cancelled while its payment was being authorized; "
                        + "Payments is asked to release it.", booking.getBookingId());
                cancellationService.notifyPayments(booking);
            }
            return;
        }

        bookingSagaRepository.findByBookingId(booking.getBookingId()).ifPresent(saga -> {
            if (saga.getStatus() != BookingSaga.Status.RUNNING
                    || !SagaStep.AUTHORIZE_PAYMENT.equals(saga.getCurrentStep())) {
                return;   // stale or repeated event
            }
            saga.advance(SagaStep.WAIT_GROUP);
            saga.reschedule(LocalDateTime.now(clock).plusMinutes(groupTimeoutMinutes));
            bookingSagaRepository.save(saga);
        });
    }
}
