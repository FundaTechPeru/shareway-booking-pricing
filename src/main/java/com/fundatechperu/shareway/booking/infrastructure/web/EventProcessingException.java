package com.fundatechperu.shareway.booking.infrastructure.web;

/**
 * A valid integration event could not be processed right now (for example a lock conflict
 * that persisted or the database is unreachable). Nothing was committed, so the answer is a
 * retryable 503 instead of a 4xx the sender would treat as definitive.
 */
public class EventProcessingException extends RuntimeException {

    public EventProcessingException(Throwable cause) {
        super("The event could not be processed now; send it again", cause);
    }
}
