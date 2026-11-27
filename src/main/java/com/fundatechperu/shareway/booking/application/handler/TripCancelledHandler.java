package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.TripCancelled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TripCancelled (Trip Execution): the trip of the group was cancelled. Every active booking of the group (requested, grouped or
 * confirmed) is cancelled without penalty with reason NO_DRIVER_AVAILABLE: its seat is
 * released, its saga compensated and, when the payment was authorized, Payments is told to
 * void it. Bookings already boarded (or further) cannot be cancelled and are left untouched.
 * Idempotent: a re-delivery finds nothing active and changes nothing.
 */
@Service
public class TripCancelledHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(TripCancelledHandler.class);

    private final BookingRepository bookingRepository;
    private final BookingCancellationService cancellationService;

    public TripCancelledHandler(BookingRepository bookingRepository, BookingCancellationService cancellationService) {
        this.bookingRepository = bookingRepository;
        this.cancellationService = cancellationService;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof TripCancelled;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        TripCancelled cancelledTrip = (TripCancelled) event;
        int cancelled = 0;
        for (Booking booking : bookingRepository.findByGroupIdAndStatusNot(
                cancelledTrip.groupId(), BookingStatus.CANCELLED)) {
            if (cancellationService.cancelBySystem(booking, CancellationReason.NO_DRIVER_AVAILABLE)) cancelled++;
        }
        log.info("TripCancelled for group {} (trip {}, reason {}): {} booking(s) cancelled.",
                cancelledTrip.groupId(), cancelledTrip.tripId(), cancelledTrip.reason(), cancelled);
    }
}
