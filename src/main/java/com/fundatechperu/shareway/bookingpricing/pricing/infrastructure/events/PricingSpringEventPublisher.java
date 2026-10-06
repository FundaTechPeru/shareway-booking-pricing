package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.events;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
@Component
public class PricingSpringEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;
    public PricingSpringEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void publish(Object event) { publisher.publishEvent(event); }
}
