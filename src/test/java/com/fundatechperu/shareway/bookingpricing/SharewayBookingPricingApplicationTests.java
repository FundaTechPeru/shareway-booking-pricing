package com.fundatechperu.shareway.bookingpricing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-that-is-at-least-32-bytes-long",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/shareway-booking-pricing"
})
@Testcontainers
class SharewayBookingPricingApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("shareway-booking-pricing")
            .withUsername("postgres")
            .withPassword("test-password");

    @Test
    void contextLoads() {
    }

}
