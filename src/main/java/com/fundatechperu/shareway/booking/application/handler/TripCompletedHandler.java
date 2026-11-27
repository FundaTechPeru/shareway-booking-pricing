package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.port.IntegrationOutbox;
import com.fundatechperu.shareway.booking.application.service.BookingCancellationService;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.pricing.application.service.FareService;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.service.GroupInfoProvider;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.FinalFareCalculated;
import com.fundatechperu.shareway.booking.shared.events.TripCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * TripCompleted (Trip Execution): the trip of the group ended.
 * - The bookings that travelled (in_progress, boarded, or confirmed but listed in
 *   boardedBookingIds) move to completed and their FINAL fare is priced with the real
 *   distance of the event (or GroupInfoProvider's when the event carries none), the same
 *   FarePolicy and the rule valid that day; FinalFareCalculated is published for each one.
 * - The bookings still confirmed that did not board are cancelled as NO_SHOW.
 * - ONE trip-completed message is queued for Payments with the completed bookings whose
 *   payment was authorized: finalAmount = min(final fare, authorized amount), because
 *   Payments refuses to capture more than it authorized. Nothing is queued when there is none.
 * Idempotent: a re-delivery finds no booking left to complete and queues nothing.
 */
@Service
public class TripCompletedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(TripCompletedHandler.class);

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final FareService fareService;
    private final GroupInfoProvider groupInfoProvider;
    private final BookingCancellationService cancellationService;
    private final IntegrationOutbox integrationOutbox;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * The EventPublisher is injected lazily because it (as InMemoryEventPublisher) depends on
     * all EventHandlers, including this one; without @Lazy the two beans would form a cycle.
     */
    public TripCompletedHandler(BookingRepository bookingRepository,
                                SeatInventoryRepository seatInventoryRepository,
                                FareService fareService,
                                GroupInfoProvider groupInfoProvider,
                                BookingCancellationService cancellationService,
                                IntegrationOutbox integrationOutbox,
                                @Lazy EventPublisher eventPublisher,
                                Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.fareService = fareService;
        this.groupInfoProvider = groupInfoProvider;
        this.cancellationService = cancellationService;
        this.integrationOutbox = integrationOutbox;
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
        Set<UUID> boarded = new HashSet<>(completed.boardedBookingIds());

        List<Booking> travelled = new ArrayList<>();
        List<Booking> noShows = new ArrayList<>();
        for (Booking booking : bookingRepository.findByGroupIdAndStatusNot(groupId, BookingStatus.CANCELLED)) {
            switch (booking.getStatus()) {
                case IN_PROGRESS, BOARDED -> travelled.add(booking);
                case CONFIRMED -> (boarded.contains(booking.getBookingId()) ? travelled : noShows).add(booking);
                default -> { }   // requested, grouped or completed: nothing to close
            }
        }
        if (travelled.isEmpty() && noShows.isEmpty()) {
            log.info("TripCompleted for group {}: no booking left to close.", groupId);
            return;
        }

        // The fare is split among the seats held when the trip ended (no-shows included),
        // the same basis as the estimate.
        int groupedPassengers = heldSeats(groupId);
        BigDecimal distanceKm = completed.distanceKm() != null
                ? completed.distanceKm() : groupInfoProvider.distanceKm(groupId);
        LocalDate today = LocalDate.now(clock);

        List<IntegrationOutbox.BookingCharge> charges = new ArrayList<>();
        for (Booking booking : travelled) {
            BigDecimal finalAmount = close(booking, today, distanceKm, groupedPassengers);
            if (booking.isPaymentAuthorized()) {
                BigDecimal capped = booking.getEstimatedAmount() == null
                        ? finalAmount : finalAmount.min(booking.getEstimatedAmount());
                if (capped.signum() > 0) {
                    charges.add(new IntegrationOutbox.BookingCharge(booking.getBookingId(), capped));
                }
            }
        }
        for (Booking booking : noShows) {
            cancellationService.cancelBySystem(booking, CancellationReason.NO_SHOW);
        }

        if (charges.isEmpty()) {
            log.info("TripCompleted for group {}: {} completed, {} no-show; nothing to capture.",
                    groupId, travelled.size(), noShows.size());
        } else if (completed.tripId() == null || completed.driverId() == null) {
            log.warn("TripCompleted for group {} carries no tripId/driverId; Payments is not notified.", groupId);
        } else {
            Instant completedAt = completed.completedAt() != null ? completed.completedAt() : completed.occurredAt();
            integrationOutbox.tripCompleted(completed.tripId(), completed.driverId(), completedAt, charges);
            log.info("TripCompleted for group {}: {} completed, {} no-show, {} charge(s) sent to Payments.",
                    groupId, travelled.size(), noShows.size(), charges.size());
        }
    }

    /** Completes the booking, persists its final fare and publishes FinalFareCalculated. */
    private BigDecimal close(Booking booking, LocalDate today, BigDecimal distanceKm, int groupedPassengers) {
        if (booking.getStatus() == BookingStatus.CONFIRMED) booking.markBoarded();
        if (booking.getStatus() == BookingStatus.BOARDED) booking.start();
        booking.complete();
        bookingRepository.save(booking);

        FareService.PricedFare finalFare = fareService.priceAndPersist(
                booking.getBookingId(), Fare.FINAL, today, distanceKm, groupedPassengers, false);

        eventPublisher.publish(new FinalFareCalculated(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), finalFare.fare().amount(), finalFare.fare().currency(),
                finalFare.ruleVersion()));
        return finalFare.fare().amount();
    }

    /** Seats held by the group (passengers sharing the trip), minimum 1. */
    private int heldSeats(UUID groupId) {
        return seatInventoryRepository.findById(groupId)
                .map(inventory -> Math.max(1, inventory.getCapacity() - inventory.getAvailableSeats()))
                .orElse(1);
    }
}
