package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.config;

import com.fundatechperu.shareway.bookingpricing.booking.domain.service.CancellationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class BookingPolicyConfiguration {
    @Bean public Clock bookingClock() { return Clock.systemUTC(); }
    @Bean public CancellationPolicy cancellationPolicy(
            @Value("${booking.cancellation.penalty-window-minutes:30}") long minutes) {
        return new CancellationPolicy(minutes);
    }
}
