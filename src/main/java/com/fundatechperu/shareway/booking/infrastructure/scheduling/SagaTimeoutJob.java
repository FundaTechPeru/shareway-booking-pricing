package com.fundatechperu.shareway.booking.infrastructure.scheduling;

import com.fundatechperu.shareway.booking.application.service.SagaTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled entry point of the saga timeout compensation (Sprint 2): runs every minute by
 * default (BOOKING_SAGA_TIMEOUT_JOB_CRON) and delegates to {@link SagaTimeoutService}.
 * Disable with BOOKING_SAGA_TIMEOUT_JOB_ENABLED=false (the tests call the service directly).
 */
@Component
@ConditionalOnProperty(name = "booking.saga.timeout-job-enabled", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutJob.class);

    private final SagaTimeoutService sagaTimeoutService;

    public SagaTimeoutJob(SagaTimeoutService sagaTimeoutService) {
        this.sagaTimeoutService = sagaTimeoutService;
    }

    @Scheduled(cron = "${booking.saga.timeout-job-cron:0 * * * * *}")
    public void compensateExpiredSagas() {
        int compensated = sagaTimeoutService.compensateExpiredSagas();
        if (compensated > 0) {
            log.info("Saga timeout job compensated {} expired saga(s).", compensated);
        }
    }
}
