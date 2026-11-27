package com.fundatechperu.shareway.booking.infrastructure.security;

import com.fundatechperu.shareway.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside the dev, test and local profiles the service refuses to start with a missing or
 * development JWT_SECRET / INTERNAL_API_KEY; inside them the development values are accepted
 * (and INTERNAL_API_KEY falls back to the shared development key).
 */
class StartupSecretsTest {

    private static final String STRONG_SECRET = "a-strong-production-secret-with-more-than-32-chars";
    /** Generated per run: no key other than the shared development one is written in the code. */
    private static final String CONFIGURED_KEY = UUID.randomUUID().toString();

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    // ------------------------------------------------------------------------ JWT_SECRET

    @Test
    void productionRejectsAMissingExampleOrShortJwtSecret() {
        for (String secret : new String[]{null, " ", JwtSecretValidator.EXAMPLE_SECRET, "too-short"}) {
            JwtSecretValidator validator = new JwtSecretValidator(new JwtProperties(secret), profile("prod"));
            assertThrows(IllegalStateException.class, validator::validate, "secret: " + secret);
        }
        assertDoesNotThrow(new JwtSecretValidator(new JwtProperties(STRONG_SECRET), profile("prod"))::validate);
        assertThrows(IllegalStateException.class,
                new JwtSecretValidator(new JwtProperties(null), profile())::validate, "no profile is production");
    }

    @Test
    void devTestAndLocalAcceptTheExampleJwtSecret() {
        for (String relaxed : new String[]{"dev", "test", "local"}) {
            assertDoesNotThrow(new JwtSecretValidator(new JwtProperties(JwtSecretValidator.EXAMPLE_SECRET),
                    profile(relaxed))::validate, relaxed);
        }
    }

    // ------------------------------------------------------------------ INTERNAL_API_KEY

    @Test
    void productionRejectsAMissingOrDevelopmentInternalKey() {
        for (String key : new String[]{null, "", "  ", InternalApiKey.DEVELOPMENT_API_KEY}) {
            assertThrows(IllegalStateException.class,
                    () -> new InternalApiKey(new InternalApiProperties(key), profile("prod")), "key: " + key);
        }
        InternalApiKey key = new InternalApiKey(new InternalApiProperties(CONFIGURED_KEY), profile("prod"));
        assertEquals(CONFIGURED_KEY, key.value());
    }

    @Test
    void devTestAndLocalFallBackToTheDevelopmentKey() {
        for (String relaxed : new String[]{"dev", "test", "local"}) {
            InternalApiKey key = new InternalApiKey(new InternalApiProperties(null), profile(relaxed));
            assertEquals(InternalApiKey.DEVELOPMENT_API_KEY, key.value(), relaxed);
        }
        InternalApiKey configured = new InternalApiKey(new InternalApiProperties(CONFIGURED_KEY), profile("dev"));
        assertEquals(CONFIGURED_KEY, configured.value(), "a configured key still wins in dev");
    }

    @Test
    void theReceivedKeyMustMatchExactly() {
        InternalApiKey key = new InternalApiKey(new InternalApiProperties(CONFIGURED_KEY), profile("prod"));
        assertTrue(key.matches(CONFIGURED_KEY));
        assertFalse(key.matches(null));
        assertFalse(key.matches(""));
        assertFalse(key.matches(CONFIGURED_KEY + " "));
        assertFalse(key.matches(CONFIGURED_KEY.toUpperCase()));
    }

    // ------------------------------------------------------------------------ JwtService

    @Test
    void theJwtServiceReadsTheSubjectAndTheRole() {
        JwtService service = new JwtService(new JwtProperties(TestTokens.SECRET));
        UUID userId = UUID.randomUUID();

        AuthenticatedUser user = service.authenticate(
                TestTokens.token(userId, "passenger")).orElseThrow();
        assertEquals(userId, user.id());
        assertEquals(UserRole.PASSENGER, user.role(), "the role is case-insensitive");

        assertNull(service.authenticate(TestTokens.withoutRole(userId))
                .orElseThrow().role());
        assertTrue(service.authenticate(TestTokens.expired(userId, "ADMIN"))
                .isEmpty());
        assertTrue(service.authenticate(TestTokens
                .signedWithAnotherSecret(userId, "ADMIN")).isEmpty());
        assertTrue(service.authenticate("garbage").isEmpty());
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("")));
    }
}
