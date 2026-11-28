package com.fundatechperu.shareway.booking.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param apiKey key shared by the ShareWay services (INTERNAL_API_KEY) */
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiProperties(String apiKey) {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
}
