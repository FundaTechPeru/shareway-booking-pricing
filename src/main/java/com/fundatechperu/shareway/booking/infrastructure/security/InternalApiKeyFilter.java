package com.fundatechperu.shareway.booking.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Protects {@code /api/v1/internal/**} with the X-Internal-Api-Key header, compared in
 * constant time: 401 when it is missing or does not match. A JWT is never accepted there.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
    public static final String INTERNAL_ROLE = "INTERNAL_SERVICE";

    private final InternalApiKey internalApiKey;
    private final SecurityErrorWriter errorWriter;

    public InternalApiKeyFilter(InternalApiKey internalApiKey, SecurityErrorWriter errorWriter) {
        this.internalApiKey = internalApiKey;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!internalApiKey.matches(request.getHeader(InternalApiProperties.API_KEY_HEADER))) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Missing or invalid internal API key");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken("internal-service", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + INTERNAL_ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isInternal(request);
    }

    static boolean isInternal(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + INTERNAL_PATH_PREFIX);
    }
}
