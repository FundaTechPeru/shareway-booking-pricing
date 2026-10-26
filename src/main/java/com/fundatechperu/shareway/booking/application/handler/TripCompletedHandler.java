package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.FinalFareCalculated;
import com.fundatechperu.shareway.booking.shared.events.TripCompleted;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * TripCompleted (closing of the booking, Sprint 2): the trip of the group ended. The
 * in_progress bookings of the group move to completed and, for each one, the FINAL fare is
 * priced with the REAL distance carried by the event, the same FarePolicy strategy and
 * the rule valid that day. The fares row (fare_type final, UNIQUE with booking_id) is
 * created once: a re-delivery never duplicates it. Finally FinalFareCalculated is
 * published with amount, currency and rule version. Idempotent: bookings already
 * completed are not selected again, so nothing is republished.
 */
@Service
public class TripCompletedHandler implements EventHandler {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final FareService fareService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * The EventPublisher is injected lazily because it (as InMemoryEventPublisher) depends on
     * all EventHandlers, including this one; without @Lazy the two beans would form a cycle.
     */
    public TripCompletedHandler(BookingRepository bookingRepository,
                                SeatInventoryRepository seatInventoryRepository,
                                FareService fareService,
                                @Lazy EventPublisher eventPublisher,
                                Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.fareService = fareService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof TripCompleted;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        TripCompleted completed = (TripCompleted) event;
        UUID groupId = completed.groupId();
        BigDecimal distanceKm = completed.distanceKm();
        LocalDate today = LocalDate.now(clock);
        int groupedPassengers = heldSeats(groupId);
        bookingRepository.findByGroupIdAndStatus(groupId, BookingStatus.IN_PROGRESS)
                .forEach(booking -> close(booking, today, distanceKm, groupedPassengers));
    }

    private void close(Booking booking, LocalDate today, BigDecimal distanceKm, int groupedPassengers) {
        booking.complete();
        bookingRepository.save(booking);

        FareService.PricedFare finalFare = fareService.priceAndPersist(
                booking.getBookingId(), Fare.FINAL, today, distanceKm, groupedPassengers, false);

        eventPublisher.publish(new FinalFareCalculated(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), finalFare.fare().amount(), finalFare.fare().currency(),
                finalFare.ruleVersion()));
    }

    /** Seats held by the group (passengers sharing the trip), minimum 1. */
    private int heldSeats(UUID groupId) {
        return seatInventoryRepository.findById(groupId)
                .map(inventory -> Math.max(1, inventory.getCapacity() - inventory.getAvailableSeats()))
                .orElse(1);
    }
}
