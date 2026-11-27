package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.shared.events.BoardingValidated;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BoardingValidated: the passenger scanned the boarding code and the driver validated it.
 * The booking moves from confirmed to boarded. Idempotent: a booking already boarded (or
 * further), in another status or unknown is left untouched and no error is raised.
 */
@Service
public class BoardingValidatedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(BoardingValidatedHandler.class);

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
        Booking booking = bookingRepository.findById(validated.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("BoardingValidated for unknown booking {}; ignored.", validated.bookingId());
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            booking.markBoarded();
            bookingRepository.save(booking);
        } else {
            log.info("BoardingValidated for booking {} in status {}; ignored.",
                    booking.getBookingId(), booking.getStatus());
        }
    }
}
