package com.fundatechperu.shareway.booking.infrastructure.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Base URLs of the services this one calls. The timeout is long because free Render services
 * can take up to a minute to wake up.
 *
 * @param paymentsUrl      Payments &amp; Settlement (PAYMENTS_URL)
 * @param tripExecutionUrl Trip Execution (TRIP_EXECUTION_URL)
 * @param timeout          connect and read timeout of every call
 */
@ConfigurationProperties(prefix = "app.integration")
public record IntegrationProperties(
        @DefaultValue(IntegrationProperties.DEFAULT_PAYMENTS_URL) String paymentsUrl,
        @DefaultValue(IntegrationProperties.DEFAULT_TRIP_EXECUTION_URL) String tripExecutionUrl,
        @DefaultValue("90s") Duration timeout) {

    public static final String DEFAULT_PAYMENTS_URL = "https://shareway-payments-settlement-service.onrender.com";
    public static final String DEFAULT_TRIP_EXECUTION_URL = "https://shareway-trip-execution.onrender.com";

    public IntegrationProperties {
        // A variable declared but left empty (for example in Render) falls back to the default.
        paymentsUrl = stripTrailingSlash(paymentsUrl == null || paymentsUrl.isBlank()
                ? DEFAULT_PAYMENTS_URL : paymentsUrl);
        tripExecutionUrl = stripTrailingSlash(tripExecutionUrl == null || tripExecutionUrl.isBlank()
                ? DEFAULT_TRIP_EXECUTION_URL : tripExecutionUrl);
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
