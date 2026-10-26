package com.fundatechperu.shareway.booking.shared.events;

/**
 * Consumer of an integration event. {@link EventPublisher} delivers the event to every
 * handler whose {@link #supports(Object)} returns true.
 */
public interface EventHandler {

    boolean supports(Object event);

    void onEvent(Object event);
}
