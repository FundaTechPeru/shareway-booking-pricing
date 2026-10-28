package com.fundatechperu.shareway.booking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.regex.Pattern;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractPostgresIntegrationTest {

    private static final String SAFETY_MESSAGE =
            "Las pruebas solo pueden correr contra una base cuyo nombre termine en -test";
    private static final Pattern SAFE_URL = Pattern.compile(
            "jdbc:postgresql://localhost(?::5432)?/[^/?]+-test(?:\\?.*)?");

    @Autowired
    protected Flyway flyway;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

    static {
        verifyAllConfiguredUrls();
    }

    @BeforeAll
    void resetSchemaBeforeTestClass() {
        verifyAllConfiguredUrls();
        verifyActualDataSourceUrl();
        flyway.clean();
        flyway.migrate();
    }

    protected void truncateServiceTables() {
        verifyAllConfiguredUrls();
        verifyActualDataSourceUrl();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    fares,
                    penalties,
                    booking_sagas,
                    bookings,
                    fare_rules,
                    seat_inventories
                CASCADE
                """);
    }

    private static String resolveConfiguredUrl() {
        String systemUrl = System.getProperty("TEST_DB_URL");
        if (systemUrl != null && !systemUrl.isBlank()) {
            return systemUrl;
        }
        return System.getenv().getOrDefault(
                "TEST_DB_URL",
                "jdbc:postgresql://localhost:5432/shareway-booking-pricing-test");
    }

    /**
     * Checks every URL source before any destructive operation. The static block runs at
     * class-loading (test discovery), which is before the Spring context starts, so a bad
     * override is caught before Flyway migrates automatically at startup.
     */
    private static void verifyAllConfiguredUrls() {
        verifyConfiguredUrl(resolveConfiguredUrl());
        verifyDatasourceOverride();
    }

    /**
     * Spring resolves spring.datasource.url from the system property or from the
     * SPRING_DATASOURCE_URL environment variable, sources that resolveConfiguredUrl()
     * does not read; an override there would silently move the real pool away from the
     * guarded TEST_DB_URL, so it must pass the same guard.
     */
    private static void verifyDatasourceOverride() {
        String override = System.getProperty("spring.datasource.url");
        if (override == null || override.isBlank()) {
            override = System.getenv("SPRING_DATASOURCE_URL");
        }
        if (override == null || override.isBlank()) {
            override = datasourceUrlInsideSpringJson(
                    System.getProperty("spring.application.json"),
                    System.getenv("SPRING_APPLICATION_JSON"));
        }
        if (override != null && !override.isBlank()) {
            verifyConfiguredUrl(override);
        }
    }

    /**
     * SPRING_APPLICATION_JSON can also carry spring.datasource.url. The value is extracted
     * with a regex on purpose: JSON is never parsed here, and a URL that cannot be read
     * simply falls through to the checks above.
     */
    private static String datasourceUrlInsideSpringJson(String... jsonSources) {
        java.util.regex.Matcher matcher = Pattern
                .compile("\"spring\\.datasource\\.url\"\\s*:\\s*\"([^\"]+)\"")
                .matcher("");
        for (String json : jsonSources) {
            if (json != null && matcher.reset(json).find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * The configured URL check above can be bypassed (SPRING_DATASOURCE_URL or
     * --spring.datasource.url point the real pool somewhere else), so the URL of the
     * opened connection is verified as well, right before any destructive operation.
     */
    private void verifyActualDataSourceUrl() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            verifyConfiguredUrl(connection.getMetaData().getURL());
        } catch (java.sql.SQLException exception) {
            throw new TestAbortedException("No se pudo verificar la URL real del DataSource", exception);
        }
    }

    private static void verifyConfiguredUrl(String url) {
        if (url == null || !SAFE_URL.matcher(url).matches()) {
            throw new TestAbortedException(SAFETY_MESSAGE + ". URL recibida: " + url);
        }
    }
}
