package com.fundatechperu.shareway.booking.infrastructure.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;

/**
 * The X-Internal-Api-Key of this service: expected on /api/v1/internal/** and sent on every
 * call to Payments and Trip Execution. Outside the dev, test and local profiles the service
 * refuses to start when INTERNAL_API_KEY is missing or still is the shared development value;
 * inside them that development value is used when INTERNAL_API_KEY is not set.
 */
@Component
public class InternalApiKey {

    /** The development value shared by every ShareWay service; it must stay in sync with them. */
    public static final String DEVELOPMENT_API_KEY = "dev-only-shareway-internal-api-key";
    private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test", "local");

    private final String value;

    public InternalApiKey(InternalApiProperties properties, Environment environment) {
        this.value = resolve(properties.apiKey(),
                Arrays.stream(environment.getActiveProfiles()).anyMatch(RELAXED_PROFILES::contains));
    }

    static String resolve(String configured, boolean relaxedProfile) {
        boolean missing = configured == null || configured.isBlank();
        if (relaxedProfile) {
            return missing ? DEVELOPMENT_API_KEY : configured;
        }
        if (missing) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY must be set in production. Configure the INTERNAL_API_KEY environment "
                            + "variable with the key shared by the ShareWay services.");
        }
        if (DEVELOPMENT_API_KEY.equals(configured)) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY is still the development value. Set the production key shared by the "
                            + "ShareWay services.");
        }
        return configured;
    }

    /** Value to send in the X-Internal-Api-Key header. */
    public String value() {
        return value;
    }

    /** Constant-time comparison of a received header value. */
    public boolean matches(String provided) {
        return provided != null && MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
