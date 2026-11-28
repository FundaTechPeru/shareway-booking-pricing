package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.GroupRejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GroupRejected (Trip Planning): the driver rejected the group. Every active booking of the group (requested, grouped or
 * confirmed) is cancelled without penalty with reason NO_DRIVER_AVAILABLE: its seat is
 * released, its saga compensated and, when the payment was authorized, Payments is told to
 * void it. Bookings already boarded (or further) cannot be cancelled and are left untouched.
 * Idempotent: a re-delivery finds nothing active and changes nothing.
 */
@Service
public class GroupRejectedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupRejectedHandler.class);

    private final BookingRepository bookingRepository;
    private final BookingCancellationService cancellationService;

    public GroupRejectedHandler(BookingRepository bookingRepository, BookingCancellationService cancellationService) {
        this.bookingRepository = bookingRepository;
        this.cancellationService = cancellationService;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof GroupRejected;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        GroupRejected rejected = (GroupRejected) event;
        int cancelled = 0;
        for (Booking booking : bookingRepository.findByGroupIdAndStatusNot(
                rejected.groupId(), BookingStatus.CANCELLED)) {
            if (cancellationService.cancelBySystem(booking, CancellationReason.NO_DRIVER_AVAILABLE)) cancelled++;
        }
        log.info("GroupRejected for group {} (driver {}): {} booking(s) cancelled.",
                rejected.groupId(), rejected.driverId(), cancelled);
    }
}
