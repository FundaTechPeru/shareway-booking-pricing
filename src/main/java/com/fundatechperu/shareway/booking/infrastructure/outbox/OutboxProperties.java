package com.fundatechperu.shareway.booking.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param relayEnabled   whether the scheduled relay runs (OUTBOX_RELAY_ENABLED; tests turn it off
 *                       and call the relay directly)
 * @param pollInterval   delay between two relay runs
 * @param batchSize      maximum messages sent per run
 * @param initialBackoff wait after the first retryable error; it doubles on each new error
 * @param maxBackoff     upper bound of the wait between two attempts (5 minutes)
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("5s") Duration pollInterval,
        @DefaultValue("50") int batchSize,
        @DefaultValue("5s") Duration initialBackoff,
        @DefaultValue("5m") Duration maxBackoff) {
}
