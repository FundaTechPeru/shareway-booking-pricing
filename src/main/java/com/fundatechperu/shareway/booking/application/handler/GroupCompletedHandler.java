package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.GroupCompleted;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * GroupCompleted: the trip group filled up. The requested bookings of the group move to
 * grouped and their running saga advances to SEARCH_DRIVER with the driver-search
 * deadline. Idempotent: bookings already grouped (or further) are not selected again, so a
 * re-delivery changes nothing and raises no error.
 */
@Service
public class GroupCompletedHandler implements EventHandler {

    private final BookingRepository bookingRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final Clock clock;
    private final long driverTimeoutMinutes;

    public GroupCompletedHandler(BookingRepository bookingRepository,
                                 BookingSagaRepository bookingSagaRepository,
                                 Clock clock,
                                 @Value("${booking.saga.driver-timeout-minutes:30}") long driverTimeoutMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.clock = clock;
        this.driverTimeoutMinutes = driverTimeoutMinutes;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof GroupCompleted;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        GroupCompleted completed = (GroupCompleted) event;
        bookingRepository.findByGroupIdAndStatus(completed.groupId(), BookingStatus.REQUESTED)
                .forEach(this::group);
    }

    private void group(Booking booking) {
        booking.group();
        bookingRepository.save(booking);
        bookingSagaRepository.findByBookingId(booking.getBookingId()).ifPresent(saga -> {
            if (saga.getStatus() == BookingSaga.Status.RUNNING) {
                saga.advance(SagaStep.SEARCH_DRIVER);
                saga.reschedule(LocalDateTime.now(clock).plusMinutes(driverTimeoutMinutes));
                bookingSagaRepository.save(saga);
            }
        });
    }
}
