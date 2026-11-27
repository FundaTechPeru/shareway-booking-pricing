package com.fundatechperu.shareway.booking.infrastructure;

import com.fundatechperu.shareway.booking.infrastructure.security.InternalApiProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String INTERNAL_API_KEY_SCHEME = "internalApiKey";

    @Bean
    public OpenAPI shareWayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShareWay Booking & Pricing API")
                        .version("1.0")
                        .description("ShareWay API for booking seat reservations, fare-rule management, and fare "
                                + "estimates. Public routes require a Bearer access token issued by Driver Management "
                                + "(HS256, shared JWT_SECRET): the caller is the sub claim and its role the role claim. "
                                + "POST /api/v1/internal/events only accepts the X-Internal-Api-Key header shared by "
                                + "the ShareWay services."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token issued by Driver Management (POST /api/v1/auth/login)."))
                        .addSecuritySchemes(INTERNAL_API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(InternalApiProperties.API_KEY_HEADER)
                                .description("Key shared by the ShareWay services (INTERNAL_API_KEY).")));
    }
}
