package com.fundatechperu.shareway.booking.shared.events;

/**
 * In-process event bus required by AGENTS.md: events travel through this interface with an
 * in-memory implementation (no RabbitMQ). Publishing is synchronous and happens in the same
 * JVM; a durable bus with retries and an Outbox is future work.
 */
public interface EventPublisher {

    void publish(Object event);
}
