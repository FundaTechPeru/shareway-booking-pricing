package com.fundatechperu.shareway.booking.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI shareWayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShareWay Booking & Pricing API")
                        .version("1.0")
                        .description("ShareWay API for booking seat reservations, fare-rule management, and fare "
                                + "estimates. Authentication is performed by the API Gateway: the service reads the "
                                + "already-validated X-User-Id header and exposes no JWT security scheme."));
    }
}
