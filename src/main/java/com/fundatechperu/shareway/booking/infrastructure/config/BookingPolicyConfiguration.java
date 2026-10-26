package com.fundatechperu.shareway.booking.infrastructure.config;

import com.fundatechperu.shareway.booking.domain.service.BoardingCodeGenerator;
import com.fundatechperu.shareway.booking.domain.service.CancellationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Wires the domain strategies into Spring: the Clock, the CancellationPolicy Strategy
 * (penalty window, 20 % percent and 1.00 floor, all configurable by environment
 * variables) and the BoardingCodeGenerator of the confirmation flow.
 */
@Configuration
public class BookingPolicyConfiguration {

    @Bean
    public Clock bookingClock() {
        return Clock.systemUTC();
    }

    @Bean
    public CancellationPolicy cancellationPolicy(
            @Value("${booking.cancellation.penalty-window-minutes:30}") long penaltyWindowMinutes,
            @Value("${booking.cancellation.penalty-percent:20}") long penaltyPercent,
            @Value("${booking.cancellation.minimum-penalty-amount:1.00}") BigDecimal minimumPenaltyAmount) {
        return new CancellationPolicy(penaltyWindowMinutes, BigDecimal.valueOf(penaltyPercent),
                minimumPenaltyAmount);
    }

    @Bean
    public BoardingCodeGenerator boardingCodeGenerator() {
        return new BoardingCodeGenerator();
    }
}
