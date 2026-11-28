package com.fundatechperu.shareway.booking.application.port;

import java.util.UUID;

/** Ids of the integration events already processed (processed_events). */
public interface ProcessedEventStore {

    /**
     * Records the event inside the caller's transaction.
     *
     * @return false when the event id was already recorded (a re-delivery)
     */
    boolean markProcessed(UUID eventId, String type);
}
