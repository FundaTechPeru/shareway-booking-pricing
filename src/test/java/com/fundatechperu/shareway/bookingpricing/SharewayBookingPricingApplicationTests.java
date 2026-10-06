package com.fundatechperu.shareway.bookingpricing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SharewayBookingPricingApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoadsWithFlywayAndHibernateValidation() {
        org.junit.jupiter.api.Assertions.assertEquals("5", flyway.info().current().getVersion().toString());
    }

}
