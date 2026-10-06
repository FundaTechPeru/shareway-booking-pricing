package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.events;
import com.fundatechperu.shareway.bookingpricing.booking.domain.service.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
@Component
public class BookingSpringEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;
    public BookingSpringEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void publish(Object event) { publisher.publishEvent(event); }
}
