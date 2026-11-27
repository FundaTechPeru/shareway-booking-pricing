package com.fundatechperu.shareway.booking.shared.events;

/**
 * In-process event bus: every event, whether it arrived through POST /api/v1/internal/events
 * or was produced inside this service, is dispatched synchronously to the handlers in the
 * caller's transaction. Calls to other services never go through it: they are stored in the
 * outbox (same transaction) and sent by the outbox relay.
 */
public interface EventPublisher {

    void publish(Object event);
}
