package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PaymentAuthorized: the saga advances from AUTHORIZE_PAYMENT to WAIT_GROUP and keeps
 * running. Events for a saga that already finished (compensated) are ignored.
 */
@Service
public class PaymentAuthorizedHandler implements EventHandler {

    private final BookingSagaRepository bookingSagaRepository;

    public PaymentAuthorizedHandler(BookingSagaRepository bookingSagaRepository) {
        this.bookingSagaRepository = bookingSagaRepository;
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
        bookingSagaRepository.save(saga);
    }
}
