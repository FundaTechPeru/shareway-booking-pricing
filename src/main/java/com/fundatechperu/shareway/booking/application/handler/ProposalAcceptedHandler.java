package com.fundatechperu.shareway.booking.application.handler;

import com.fundatechperu.shareway.booking.application.port.IntegrationOutbox;
import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.domain.service.BoardingCodeGenerator;
import com.fundatechperu.shareway.booking.infrastructure.security.BoardingCodeHasher;
import com.fundatechperu.shareway.booking.shared.events.BookingConfirmed;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.ProposalAccepted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProposalAccepted (Trip Planning): a driver accepted the group. The grouped bookings of the
 * group move to confirmed and, for each one:
 * 1. a boarding PIN (6 digits) and a random QR token are generated (BoardingCodeGenerator);
 * 2. the clear codes (shown only to the owner) and their hashes (BCrypt for the PIN, SHA-256
 *    for the token) are stored in bookings;
 * 3. BookingConfirmed is published once;
 * 4. the saga completes.
 * Then ONE GroupConfirmed message is queued for Trip Execution with every booking confirmed
 * here and the SHA-256 of its codes, so Trip Execution can create the trip and validate the
 * boarding. Idempotent: bookings already confirmed are not selected again, so a re-delivery
 * confirms nothing and queues nothing.
 */
@Service
public class ProposalAcceptedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProposalAcceptedHandler.class);

    private final BookingRepository bookingRepository;
    private final BookingSagaRepository bookingSagaRepository;
    private final BoardingCodeGenerator boardingCodeGenerator;
    private final BoardingCodeHasher boardingCodeHasher;
    private final EventPublisher eventPublisher;
    private final IntegrationOutbox integrationOutbox;
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
                                   IntegrationOutbox integrationOutbox,
                                   Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingSagaRepository = bookingSagaRepository;
        this.boardingCodeGenerator = boardingCodeGenerator;
        this.boardingCodeHasher = boardingCodeHasher;
        this.eventPublisher = eventPublisher;
        this.integrationOutbox = integrationOutbox;
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
        List<IntegrationOutbox.ConfirmedPassenger> passengers = new ArrayList<>();
        for (Booking booking : bookingRepository.findByGroupIdAndStatus(accepted.groupId(), BookingStatus.GROUPED)) {
            passengers.add(confirm(booking, accepted.driverId()));
        }
        if (passengers.isEmpty()) {
            log.info("ProposalAccepted {}: no grouped booking to confirm.", accepted.groupId());
            return;
        }
        integrationOutbox.groupConfirmed(accepted.groupId(), accepted.driverId(), passengers);
        log.info("ProposalAccepted {}: {} booking(s) confirmed.", accepted.groupId(), passengers.size());
    }

    private IntegrationOutbox.ConfirmedPassenger confirm(Booking booking, UUID driverId) {
        String boardingPin = boardingCodeGenerator.pin();
        String qrToken = boardingCodeGenerator.qrToken();

        booking.confirm();
        booking.assignBoardingCodes(boardingPin, qrToken, boardingCodeHasher.hashPin(boardingPin),
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

        return new IntegrationOutbox.ConfirmedPassenger(booking.getBookingId(), booking.getPassengerId(),
                boardingCodeHasher.sha256Hex(qrToken), boardingCodeHasher.sha256Hex(boardingPin));
    }
}
