package com.fundatechperu.shareway.booking.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security of the service:
 * - Bearer JWT (HS256, JWT_SECRET) on the public API; the caller is the {@code sub} claim and
 *   its role the {@code role} claim. X-User-Id is no longer read.
 * - POST /api/v1/bookings requires the PASSENGER role; the other booking routes, fares and
 *   fare rules require any authenticated user (ownership is checked by BookingService).
 * - /api/v1/internal/** only accepts X-Internal-Api-Key (InternalApiKeyFilter).
 * - actuator health, Swagger UI and the OpenAPI document are public.
 * Missing or invalid credentials answer 401 and a missing role 403, with the uniform error body.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, InternalApiKey internalApiKey,
                                            SecurityErrorWriter errorWriter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey, errorWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(request,
                                response, HttpStatus.UNAUTHORIZED, "Missing, invalid or expired access token"))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(request,
                                response, HttpStatus.FORBIDDEN, "The role of the access token cannot use this operation")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        // Only registered with the dev profile (DevEventController).
                        .requestMatchers("/dev/**").permitAll()
                        .requestMatchers("/api/v1/internal/**").hasRole(InternalApiKeyFilter.INTERNAL_ROLE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasRole(UserRole.PASSENGER.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings")
                        .hasAnyRole(UserRole.PASSENGER.name(), UserRole.ADMIN.name())
                        .anyRequest().authenticated());
        return http.build();
    }
}
