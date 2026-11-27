package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.port.IntegrationOutbox;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.CancellationReason;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.domain.service.DepartureTimeProvider;
import com.fundatechperu.shareway.booking.shared.events.BookingCancelled;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Cancellation steps shared by every path that cancels a booking: the passenger, the saga
 * timeout, a rejected payment, a rejected group, a cancelled trip and a no-show.
 * The seat goes back to the inventory, the saga is compensated, BookingCancelled is published
 * and, when Payments had authorized the payment, the booking-cancelled message is queued in
 * the outbox (same transaction) so Payments voids the authorization or charges the penalty.
 */
@Service
public class BookingCancellationService {

    private static final Logger log = LoggerFactory.getLogger(BookingCancellationService.class);

    private final BookingRepository bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final EventPublisher eventPublisher;
    private final IntegrationOutbox integrationOutbox;
    private final DepartureTimeProvider departureTimeProvider;
    private final Clock clock;

    /**
     * The EventPublisher is injected lazily because it depends on every EventHandler, and
     * several handlers depend on this service.
     */
    public BookingCancellationService(BookingRepository bookingRepository,
                                      SeatInventoryRepository seatInventoryRepository,
                                      BookingSagaRepository bookingSagaRepository,
                                      @Lazy EventPublisher eventPublisher,
                                      IntegrationOutbox integrationOutbox,
                                      DepartureTimeProvider departureTimeProvider,
                                      Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.eventPublisher = eventPublisher;
        this.integrationOutbox = integrationOutbox;
        this.departureTimeProvider = departureTimeProvider;
        this.clock = clock;
    }

    /**
     * Cancels a booking on behalf of the system, without penalty.
     *
     * @return false when the booking can no longer be cancelled (it is left untouched)
     */
    @Transactional
    public boolean cancelBySystem(Booking booking, CancellationReason reason) {
        if (!booking.isCancellable()) {
            log.info("Booking {} is {} and cannot be cancelled ({}); left untouched.",
                    booking.getBookingId(), booking.getStatus(), reason);
            return false;
        }
        cancel(booking, reason);
        notifyCancelled(booking, false);
        log.info("Booking {} cancelled ({}).", booking.getBookingId(), reason);
        return true;
    }

    /**
     * Moves the booking to cancelled, releases its seat (never above capacity) and
     * compensates its saga. Nothing is published: see {@link #notifyCancelled}.
     */
    @Transactional
    public void cancel(Booking booking, CancellationReason reason) {
        booking.cancel(reason, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        bookingRepository.save(booking);

        seatInventoryRepository.findByIdForUpdate(booking.getGroupId()).ifPresentOrElse(inventory -> {
            inventory.releaseSeat();
            seatInventoryRepository.save(inventory);
        }, () -> log.warn("Booking {} cancelled but its group {} has no seat inventory.",
                booking.getBookingId(), booking.getGroupId()));

        bookingSagaRepository.findByBookingId(booking.getBookingId()).ifPresent(saga -> {
            if (saga.getStatus() != BookingSaga.Status.COMPENSATED) {
                saga.compensate();
                bookingSagaRepository.save(saga);
            }
        });
    }

    /**
     * Publishes BookingCancelled and, when the payment was authorized, queues the
     * booking-cancelled message for Payments.
     */
    @Transactional
    public void notifyCancelled(Booking booking, boolean penaltyApplicable) {
        eventPublisher.publish(new BookingCancelled(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), booking.getGroupId(), booking.getPassengerId(), penaltyApplicable,
                booking.getCancellationReason()));
        if (booking.isPaymentAuthorized()) {
            notifyPayments(booking);
        }
    }

    /** Queues the booking-cancelled message for Payments (the booking is already cancelled). */
    @Transactional
    public void notifyPayments(Booking booking) {
        Instant cancelledAt = booking.getCancelledAt() == null
                ? clock.instant() : booking.getCancelledAt().toInstant(ZoneOffset.UTC);
        Instant departureAt = departureTimeProvider.departureAt(booking.getGroupId());
        CancellationReason reason = booking.getCancellationReason() == null
                ? CancellationReason.NO_DRIVER_AVAILABLE : booking.getCancellationReason();
        integrationOutbox.bookingCancelled(booking.getBookingId(), reason, cancelledAt,
                departureAt == null ? cancelledAt : departureAt);
    }
}
