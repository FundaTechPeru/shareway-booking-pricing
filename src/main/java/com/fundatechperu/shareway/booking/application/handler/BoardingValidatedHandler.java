package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.exception.BookingNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.shared.events.BoardingValidated;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BoardingValidated: the passenger scanned the boarding code and the driver validated it.
 * The booking moves from confirmed to boarded. Idempotent: a booking already boarded (or
 * further) is left untouched and no error is raised.
 */
@Service
public class BoardingValidatedHandler implements EventHandler {

    private final BookingRepository bookingRepository;

    public BoardingValidatedHandler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof BoardingValidated;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        BoardingValidated validated = (BoardingValidated) event;
        Booking booking = bookingRepository.findById(validated.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(validated.bookingId()));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            booking.markBoarded();
            bookingRepository.save(booking);
        }
    }
}
