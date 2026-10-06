package com.fundatechperu.shareway.bookingpricing;

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
        verifyConfiguredUrl(resolveConfiguredUrl());
    }

    @BeforeAll
    void resetSchemaBeforeTestClass() {
        String url = resolveConfiguredUrl();
        verifyConfiguredUrl(url);
        flyway.clean();
        flyway.migrate();
    }

    protected void truncateServiceTables() {
        verifyConfiguredUrl(resolveConfiguredUrl());
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    processed_events,
                    payments,
                    penalties,
                    driver_settlements,
                    fares,
                    fare_rules,
                    bookings,
                    trip_groups,
                    trip_requests
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

    private static void verifyConfiguredUrl(String url) {
        if (url == null || !SAFE_URL.matcher(url).matches()) {
            throw new TestAbortedException(SAFETY_MESSAGE + ". URL recibida: " + url);
        }
    }
}
