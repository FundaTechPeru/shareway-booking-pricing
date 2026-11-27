package com.fundatechperu.shareway.booking.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundatechperu.shareway.booking.infrastructure.integration.IntegrationProperties;
import com.fundatechperu.shareway.booking.infrastructure.integration.InternalHttpClient;
import com.fundatechperu.shareway.booking.infrastructure.integration.InternalHttpClient.HttpResult;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorizationFailed;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sends the pending outbox messages in the order they were stored (sequence_number).
 * <ul>
 *   <li>2xx: delivered. For a payment authorization (200/201) PaymentAuthorized is published
 *       locally, which advances the saga.</li>
 *   <li>Payment authorization answered 402, 404 or 422: PaymentAuthorizationFailed is
 *       published locally with Payments' message (the booking is cancelled) and the message
 *       is settled.</li>
 *   <li>booking-cancelled answered 404: Payments has no payment for the booking; settled.</li>
 *   <li>Timeout, connection error, 1xx/3xx, 5xx, 401, 408 or 429: retried with exponential
 *       backoff (initial-backoff doubling up to max-backoff, 5 minutes). The batch STOPS there
 *       so no later message overtakes it.</li>
 *   <li>Any other 4xx: sending it again would fail the same way; marked failed and the batch
 *       goes on.</li>
 * </ul>
 * The HTTP call runs outside any transaction; the outcome (and the local event it triggers)
 * is applied in its own transaction. Runs are serialized in this JVM.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Set<Integer> PAYMENT_DECLINED_STATUSES = Set.of(402, 404, 422);
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(401, 408, 429);
    private static final int MAX_DETAIL_LENGTH = 500;

    private final OutboxMessageRepository repository;
    private final InternalHttpClient httpClient;
    private final IntegrationProperties integrationProperties;
    private final OutboxProperties properties;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OutboxRelay(OutboxMessageRepository repository, InternalHttpClient httpClient,
                       IntegrationProperties integrationProperties, OutboxProperties properties,
                       EventPublisher eventPublisher, ObjectMapper objectMapper,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.integrationProperties = integrationProperties;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** What happened to one message. */
    enum Outcome {
        /** 2xx (or a 404 to booking-cancelled): done. */
        DELIVERED,
        /** Payment authorization answered 402, 404 or 422: the booking is cancelled, message done. */
        PAYMENT_DECLINED,
        /** Send it again later; the batch stops. */
        RETRY,
        /** Rejected for good; the batch goes on. */
        REJECTED
    }

    /** @return how many messages were settled (delivered, declined or rejected) in this run */
    public synchronized int relayPending() {
        List<OutboxMessage> pending = repository.findPending(PageRequest.of(0, properties.batchSize()));
        int settled = 0;
        for (OutboxMessage message : pending) {
            if (message.getNextAttemptAt().isAfter(clock.instant())) {
                return settled;   // the oldest pending message is still backing off: keep the order
            }
            HttpResult result = httpClient.post(message.getType().url(integrationProperties), message.getPayload());
            Outcome outcome = classify(message.getType(), result);
            boolean settledNow;
            try {
                settledNow = Boolean.TRUE.equals(
                        transactionTemplate.execute(status -> apply(message.getId(), outcome, result)));
            } catch (RuntimeException exception) {
                log.error("Outbox message {} ({}) was answered {} but applying the answer failed; "
                        + "it will be retried.", message.getId(), message.getType(), result.status(), exception);
                transactionTemplate.executeWithoutResult(status -> retryLater(message.getId(),
                        "Local processing failed: " + exception.getMessage()));
                return settled;
            }
            if (!settledNow) {
                return settled;
            }
            settled++;
        }
        return settled;
    }

    static Outcome classify(OutboxMessageType type, HttpResult result) {
        int status = result.status();
        if (result.isIoError()) return Outcome.RETRY;
        if (status >= 200 && status < 300) return Outcome.DELIVERED;
        if (type == OutboxMessageType.PAYMENT_AUTHORIZATION && PAYMENT_DECLINED_STATUSES.contains(status)) {
            return Outcome.PAYMENT_DECLINED;
        }
        if (type == OutboxMessageType.BOOKING_CANCELLED && status == 404) return Outcome.DELIVERED;
        if (status >= 400 && status < 500 && !RETRYABLE_CLIENT_ERRORS.contains(status)) return Outcome.REJECTED;
        return Outcome.RETRY;   // 1xx, 3xx, 5xx, 401 (key not rolled out yet), 408 and 429
    }

    /** @return true when the message is settled and the batch may go on */
    private boolean apply(UUID messageId, Outcome outcome, HttpResult result) {
        OutboxMessage message = repository.findById(messageId).orElseThrow();
        Instant now = clock.instant();
        switch (outcome) {
            case DELIVERED -> {
                if (message.getType() == OutboxMessageType.PAYMENT_AUTHORIZATION) {
                    eventPublisher.publish(new PaymentAuthorized(UUID.randomUUID(), now,
                            bookingIdOf(message), paymentIdOf(result.body())));
                }
                message.markProcessed(now, result.status() >= 300 ? describe(result) : null);
                repository.save(message);
                return true;
            }
            case PAYMENT_DECLINED -> {
                String reason = errorMessageOf(result);
                eventPublisher.publish(new PaymentAuthorizationFailed(UUID.randomUUID(), now,
                        bookingIdOf(message), reason));
                message.markProcessed(now, describe(result));
                repository.save(message);
                log.info("Payment authorization of booking {} was declined: {}", bookingIdOf(message), reason);
                return true;
            }
            case REJECTED -> {
                message.markFailed(now, describe(result));
                repository.save(message);
                log.error("Outbox message {} ({}) was rejected and will not be retried: {}",
                        message.getId(), message.getType(), describe(result));
                return true;
            }
            default -> {
                retryLater(message, describe(result));
                return false;
            }
        }
    }

    private void retryLater(UUID messageId, String error) {
        repository.findById(messageId).ifPresent(message -> retryLater(message, error));
    }

    private void retryLater(OutboxMessage message, String error) {
        Duration delay = backoff(message.getAttempts() + 1);
        message.scheduleRetry(clock.instant(), delay, error);
        repository.save(message);
        log.warn("Outbox message {} ({}) will be retried in {} after attempt {}: {}",
                message.getId(), message.getType(), delay, message.getAttempts(), error);
    }

    /** Wait after {@code failedAttempts} failures: initialBackoff * 2^(failedAttempts - 1), capped. */
    public Duration backoff(int failedAttempts) {
        Duration delay = properties.initialBackoff();
        for (int i = 1; i < failedAttempts && delay.compareTo(properties.maxBackoff()) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }

    private UUID bookingIdOf(OutboxMessage message) {
        return message.getAggregateId() != null ? message.getAggregateId()
                : UUID.fromString(readTree(message.getPayload()).path("bookingId").asText());
    }

    /** The id of the payment in Payments' answer, when it can be read. */
    private UUID paymentIdOf(String body) {
        String id = readTree(body).path("id").asText(null);
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** The "message" of Payments' error body, or the status when there is none. */
    private String errorMessageOf(HttpResult result) {
        String message = readTree(result.body()).path("message").asText(null);
        return message == null || message.isBlank() ? "HTTP " + result.status() : message;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) return objectMapper.missingNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return objectMapper.missingNode();
        }
    }

    private static String describe(HttpResult result) {
        boolean noBody = result.body() == null || result.body().isBlank();
        String detail = result.isIoError()
                ? "I/O error: " + result.ioError()
                : "HTTP " + result.status() + (noBody ? "" : ": " + result.body());
        return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
