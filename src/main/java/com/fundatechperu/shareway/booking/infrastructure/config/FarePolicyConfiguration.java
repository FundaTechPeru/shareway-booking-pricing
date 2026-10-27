package com.fundatechperu.shareway.booking.infrastructure.config;

import com.fundatechperu.shareway.booking.pricing.domain.service.DistanceBasedFarePolicy;
import com.fundatechperu.shareway.booking.pricing.domain.service.FarePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the fare Strategy into Spring: the domain stays free of framework annotations,
 * the same way CancellationPolicy is exposed by {@link BookingPolicyConfiguration}.
 */
@Configuration
public class FarePolicyConfiguration {

    @Bean
    public FarePolicy farePolicy() {
        return new DistanceBasedFarePolicy();
    }
}
