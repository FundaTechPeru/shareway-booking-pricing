package com.fundatechperu.shareway.booking.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Builds HS256 access tokens shaped like the ones Driver Management issues, signed with the
 * development example secret configured for the test profile.
 */
public final class TestTokens {

    public static final String SECRET = "dev-only-shareway-driver-management-secret-change-me";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(
            "another-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8));

    private TestTokens() {
    }

    /** Token value (without the "Bearer " prefix) for a valid token. */
    public static String token(UUID userId, String role) {
        return token(userId.toString(), role, KEY, Duration.ofMinutes(60));
    }

    public static String passenger(UUID userId) {
        return token(userId, "PASSENGER");
    }

    public static String admin(UUID userId) {
        return token(userId, "ADMIN");
    }

    public static String driver(UUID userId) {
        return token(userId, "DRIVER");
    }

    public static String expired(UUID userId, String role) {
        return token(userId.toString(), role, KEY, Duration.ofMinutes(-5));
    }

    public static String signedWithAnotherSecret(UUID userId, String role) {
        return token(userId.toString(), role, OTHER_KEY, Duration.ofMinutes(60));
    }

    public static String withoutRole(UUID userId) {
        return token(userId.toString(), null, KEY, Duration.ofMinutes(60));
    }

    private static String token(String userId, String role, SecretKey key, Duration validFor) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", "user@shareway.test")
                .issuedAt(Date.from(now.minus(Duration.ofMinutes(10))))
                .expiration(Date.from(now.plus(validFor)))
                .signWith(key);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
