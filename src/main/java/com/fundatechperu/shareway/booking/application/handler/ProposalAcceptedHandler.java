package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.service.BoardingCodeGenerator;
import com.fundatechperu.shareway.booking.infrastructure.security.BoardingCodeHasher;
import com.fundatechperu.shareway.booking.shared.events.BookingConfirmed;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.ProposalAccepted;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * ProposalAccepted (confirmation of the booking, Sprint 2): a driver accepted the proposal
 * of the group. The grouped bookings of the group move to confirmed and, for each one:
 * 1. a boarding PIN (6 digits) and a random QR token are generated (BoardingCodeGenerator);
 * 2. ONLY their hashes are stored in bookings (BCrypt for the PIN, SHA-256 for the token);
 * 3. BookingConfirmed is published ONCE with the clear values (the notification service
 *    needs them); they are never returned by the API nor written to any log;
 * 4. the saga completes.
 * Idempotent: bookings already confirmed (or further) are not selected again, so a
 * re-delivery publishes nothing a second time.
 */
@Service
public class ProposalAcceptedHandler implements EventHandler {

    private final BookingRepository bookingRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final BoardingCodeGenerator boardingCodeGenerator;
    private final BoardingCodeHasher boardingCodeHasher;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * The EventPublisher is injected lazily because it (as InMemoryEventPublisher) depends on
     * all EventHandlers, including this one; without @Lazy the two beans would form a cycle.
     */
    public ProposalAcceptedHandler(BookingRepository bookingRepository,
                                   BookingSagaRepository bookingSagaRepository,
                                   BoardingCodeGenerator boardingCodeGenerator,
                                   BoardingCodeHasher boardingCodeHasher,
                                   @Lazy EventPublisher eventPublisher,
                                   Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.boardingCodeGenerator = boardingCodeGenerator;
        this.boardingCodeHasher = boardingCodeHasher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public boolean supports(Object event) {
        return event instanceof ProposalAccepted;
    }

    @Override
    @Transactional
    public void onEvent(Object event) {
        ProposalAccepted accepted = (ProposalAccepted) event;
        bookingRepository.findByGroupIdAndStatus(accepted.groupId(), BookingStatus.GROUPED)
                .forEach(booking -> confirm(booking, accepted.driverId()));
    }

    private void confirm(Booking booking, UUID driverId) {
        String boardingPin = boardingCodeGenerator.pin();
        String qrToken = boardingCodeGenerator.qrToken();

        booking.confirm();
        booking.assignBoardingCodes(boardingCodeHasher.hashPin(boardingPin),
                boardingCodeHasher.hashQrToken(qrToken));
        bookingRepository.save(booking);

        bookingSagaRepository.findByBookingId(booking.getBookingId()).ifPresent(saga -> {
            if (saga.getStatus() == BookingSaga.Status.RUNNING) {
                saga.complete();
                bookingSagaRepository.save(saga);
            }
        });

        eventPublisher.publish(new BookingConfirmed(UUID.randomUUID(), clock.instant(),
                booking.getBookingId(), booking.getGroupId(), booking.getPassengerId(), driverId,
                boardingPin, qrToken));
    }
}
