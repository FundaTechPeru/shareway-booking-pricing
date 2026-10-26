package com.fundatechperu.shareway.booking.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled jobs (Sprint 2: the booking-saga timeout compensation).
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
