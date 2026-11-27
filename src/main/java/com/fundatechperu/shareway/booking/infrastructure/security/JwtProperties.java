package com.fundatechperu.shareway.booking.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param secret HS256 key shared with Driver Management (JWT_SECRET) */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret) {
}
