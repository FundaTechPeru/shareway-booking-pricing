package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.infrastructure.events.InMemoryEventPublisher;
import com.fundatechperu.shareway.booking.shared.events.EventHandler;
import com.fundatechperu.shareway.booking.shared.events.PaymentAuthorized;
import com.fundatechperu.shareway.booking.shared.events.TripGroupOpened;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In-memory EventPublisher: synchronous dispatch only to the handlers that support the event.
 */
class InMemoryEventPublisherTest {

    private static final class RecordingHandler implements EventHandler {
        private final Class<?> type;
        private final List<Object> received = new ArrayList<>();

        private RecordingHandler(Class<?> type) {
            this.type = type;
        }

        @Override
        public boolean supports(Object event) {
            return type.isInstance(event);
        }

        @Override
        public void onEvent(Object event) {
            received.add(event);
        }
    }

    @Test
    void dispatchesOnlyToHandlersThatSupportTheEvent() {
        RecordingHandler paymentHandler = new RecordingHandler(PaymentAuthorized.class);
        RecordingHandler groupHandler = new RecordingHandler(TripGroupOpened.class);
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(List.of(paymentHandler, groupHandler));

        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), UUID.randomUUID());
        publisher.publish(event);

        assertEquals(List.of(event), paymentHandler.received);
        assertTrue(groupHandler.received.isEmpty());
    }

    @Test
    void deliversToEverySupportingHandler() {
        RecordingHandler first = new RecordingHandler(PaymentAuthorized.class);
        RecordingHandler second = new RecordingHandler(PaymentAuthorized.class);
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(List.of(first, second));

        PaymentAuthorized event = new PaymentAuthorized(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), UUID.randomUUID());
        publisher.publish(event);

        assertEquals(List.of(event), first.received);
        assertEquals(List.of(event), second.received);
    }

    @Test
    void nullEventAndEmptyRegistryAreSafe() {
        InMemoryEventPublisher publisher = new InMemoryEventPublisher(List.of());
        assertDoesNotThrow(() -> publisher.publish(null));
        assertDoesNotThrow(() -> publisher.publish("any event"));
    }
}
