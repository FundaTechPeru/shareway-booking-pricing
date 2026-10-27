package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * PaymentAuthorized: the saga advances from AUTHORIZE_PAYMENT to WAIT_GROUP, keeps running
 * and takes the group deadline (+group timeout). Events for a saga that already finished
 * (compensated) are ignored.
 */
@Service
public class PaymentAuthorizedHandler implements EventHandler {

    private final BookingSagaRepository bookingSagaRepository;
    private final Clock clock;
    private final long groupTimeoutMinutes;

    public PaymentAuthorizedHandler(BookingSagaRepository bookingSagaRepository,
                                    Clock clock,
                                    @Value("${booking.saga.group-timeout-minutes:60}") long groupTimeoutMinutes) {
        this.bookingSagaRepository = bookingSagaRepository;
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
        BookingSaga saga = bookingSagaRepository.findByBookingId(paymentAuthorized.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(paymentAuthorized.bookingId()));
        if (saga.getStatus() != BookingSaga.Status.RUNNING) return;   // stale event
        saga.advance(SagaStep.WAIT_GROUP);
        saga.reschedule(LocalDateTime.now(clock).plusMinutes(groupTimeoutMinutes));
        bookingSagaRepository.save(saga);
    }
}
