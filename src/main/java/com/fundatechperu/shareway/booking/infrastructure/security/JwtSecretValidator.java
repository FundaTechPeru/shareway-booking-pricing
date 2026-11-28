package com.fundatechperu.shareway.booking.infrastructure.security;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to start the service with a missing or example JWT secret in production. The gateway and the
 * other services trust whoever signs a token with this key, so a shared, well-known development secret
 * must never be used in production. The check is relaxed under the dev, test and local profiles, where
 * the example value in {@code application-dev.yml} (and the test configuration) is acceptable.
 *
 * <p>Copied from Driver Management, which issues the tokens.
 */
@Component
public class JwtSecretValidator {

    /** The development example value; it is copied in application-dev.yml and must stay in sync. */
    static final String EXAMPLE_SECRET = "dev-only-shareway-driver-management-secret-change-me";
    private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test", "local");
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretValidator(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (isRelaxedProfile()) {
            return;
        }
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set in production. Configure the JWT_SECRET environment variable "
                            + "with a strong random value of at least " + MIN_SECRET_LENGTH + " characters.");
        }
        if (EXAMPLE_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the development example value. Set a strong, unique JWT_SECRET in production.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short; use at least " + MIN_SECRET_LENGTH + " characters.");
        }
    }

    private boolean isRelaxedProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(RELAXED_PROFILES::contains);
    }
}
