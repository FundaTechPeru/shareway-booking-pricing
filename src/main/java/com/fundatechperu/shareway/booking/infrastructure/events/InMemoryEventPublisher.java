package com.fundatechperu.shareway.booking.infrastructure.events;

import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-memory {@link EventPublisher}: dispatches each event synchronously to every registered
 * {@link EventHandler} that supports it. Events live only in this JVM process; they are lost
 * if the application stops while handling them (documented limitation of phase 1).
 */
@Component
public class InMemoryEventPublisher implements EventPublisher {

    private final List<EventHandler> handlers;

    public InMemoryEventPublisher(List<EventHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void publish(Object event) {
        if (event == null) return;
        for (EventHandler handler : handlers) {
            if (handler.supports(event)) handler.onEvent(event);
        }
    }
}
