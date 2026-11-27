package com.fundatechperu.shareway.booking.application.service;

import com.fundatechperu.shareway.booking.application.port.ProcessedEventStore;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Processes one integration event received through POST /api/v1/internal/events, exactly
 * once per eventId: the processed_events row and every change the handlers make (including the
 * outbox messages they queue) are committed in ONE transaction. A repeated eventId is
 * acknowledged without dispatching it again. Optimistic-lock conflicts (a passenger booking
 * the same group at the same moment) are retried; any other failure rolls everything back, so
 * the sender may deliver the event again.
 */
@Service
public class InternalEventService {

    private static final Logger log = LoggerFactory.getLogger(InternalEventService.class);

    private final ProcessedEventStore processedEventStore;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final long backoffMs;

    public InternalEventService(ProcessedEventStore processedEventStore,
                                EventPublisher eventPublisher,
                                PlatformTransactionManager transactionManager,
                                @Value("${booking.reservation.max-attempts:3}") int maxAttempts,
                                @Value("${booking.reservation.backoff-ms:20}") long backoffMs) {
        this.processedEventStore = processedEventStore;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** @return false when the event id had already been processed (nothing was dispatched) */
    public boolean process(UUID eventId, String type, Object event) {
        for (int attempt = 1; ; attempt++) {
            try {
                Boolean processed = transactionTemplate.execute(status -> {
                    if (!processedEventStore.markProcessed(eventId, type)) {
                        return false;
                    }
                    eventPublisher.publish(event);
                    return true;
                });
                if (Boolean.TRUE.equals(processed)) {
                    log.info("Integration event {} ({}) processed.", eventId, type);
                    return true;
                }
                log.info("Integration event {} ({}) was already processed; ignored.", eventId, type);
                return false;
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException exception) {
                if (attempt >= maxAttempts) throw exception;
                log.info("Integration event {} ({}) hit a concurrent update; retrying (attempt {}).",
                        eventId, type, attempt + 1);
                pause();
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(backoffMs + (long) (Math.random() * 10));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
