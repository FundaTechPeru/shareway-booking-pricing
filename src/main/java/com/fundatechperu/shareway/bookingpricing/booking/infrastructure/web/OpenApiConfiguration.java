package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                        .description("ShareWay API for booking seat reservations, fare-rule management, fare estimates, and payment authorization."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
