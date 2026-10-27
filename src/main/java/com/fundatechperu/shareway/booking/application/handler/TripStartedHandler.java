package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.TripStarted;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TripStarted: the trip of the group departed. The boarded bookings of the group move to
 * in_progress. Idempotent: bookings already in_progress (or further) are not selected
 * again, so a re-delivery changes nothing and raises no error.
 */
@Service
public class TripStartedHandler implements EventHandler {

    private final BookingRepository bookingRepository;

    public TripStartedHandler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof TripStarted;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        TripStarted started = (TripStarted) event;
        bookingRepository.findByGroupIdAndStatus(started.groupId(), BookingStatus.BOARDED)
                .forEach(booking -> {
                    booking.start();
                    bookingRepository.save(booking);
                });
    }
}
