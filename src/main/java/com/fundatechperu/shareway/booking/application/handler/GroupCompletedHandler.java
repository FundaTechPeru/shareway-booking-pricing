package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SagaStep;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.GroupCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * GroupCompleted (Trip Planning): the trip group closed. Only the requested bookings whose
 * payment is already authorized (saga running in WAIT_GROUP) move to grouped and their saga
 * advances to SEARCH_DRIVER with the driver-search deadline. Bookings still waiting for the
 * payment stay requested: their saga keeps its own deadline. Idempotent: bookings already
 * grouped (or further) are not selected again, so a re-delivery changes nothing.
 */
@Service
public class GroupCompletedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupCompletedHandler.class);

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
        int grouped = 0;
        for (Booking booking : bookingRepository.findByGroupIdAndStatus(completed.groupId(), BookingStatus.REQUESTED)) {
            Optional<BookingSaga> saga = bookingSagaRepository.findByBookingId(booking.getBookingId());
            if (saga.isPresent() && saga.get().getStatus() == BookingSaga.Status.RUNNING
                    && SagaStep.WAIT_GROUP.equals(saga.get().getCurrentStep())) {
                group(booking, saga.get());
                grouped++;
            } else {
                log.info("Booking {} of group {} is not grouped: its payment is not authorized yet.",
                        booking.getBookingId(), completed.groupId());
            }
        }
        log.info("GroupCompleted {}: {} booking(s) grouped.", completed.groupId(), grouped);
    }

    private void group(Booking booking, BookingSaga saga) {
        booking.group();
        bookingRepository.save(booking);
        saga.advance(SagaStep.SEARCH_DRIVER);
        saga.reschedule(LocalDateTime.now(clock).plusMinutes(driverTimeoutMinutes));
        bookingSagaRepository.save(saga);
    }
}
