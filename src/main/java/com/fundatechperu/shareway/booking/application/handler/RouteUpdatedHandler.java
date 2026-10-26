package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.exception.GroupNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.RouteUpdated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RouteUpdated: the route distance of the group changed, so the ESTIMATED fare of every
 * active booking of the group is recalculated with the rule valid today and its fares row
 * is updated in place (UNIQUE booking_id + fare_type, never duplicated). The passenger
 * count is the seats already held in the group. No event is published in return
 * (documented assumption of Sprint 2). Idempotent: re-pricing the same distance produces
 * the same amounts.
 */
@Service
public class RouteUpdatedHandler implements EventHandler {

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final FareService fareService;
    private final Clock clock;

    public RouteUpdatedHandler(BookingRepository bookingRepository,
                               SeatInventoryRepository seatInventoryRepository,
                               FareService fareService,
                               Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.fareService = fareService;
        this.clock = clock;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof RouteUpdated;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        RouteUpdated updated = (RouteUpdated) event;
        UUID groupId = updated.groupId();
        BigDecimal distanceKm = updated.distanceKm();
        LocalDate today = LocalDate.now(clock);
        int groupedPassengers = heldSeats(groupId);
        bookingRepository.findByGroupIdAndStatusNot(groupId, BookingStatus.CANCELLED)
                .forEach(booking -> fareService.priceAndPersist(booking.getBookingId(), Fare.ESTIMATED,
                        today, distanceKm, groupedPassengers, true));
    }

    /** Held seats of a group, minimum 1 (an empty group is priced as a solo rider). */
    private int heldSeats(UUID groupId) {
        SeatInventory inventory = seatInventoryRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        return Math.max(1, inventory.getCapacity() - inventory.getAvailableSeats());
    }
}
