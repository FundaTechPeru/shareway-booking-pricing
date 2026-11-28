package com.fundatechperu.shareway.booking.infrastructure.security;

import java.util.UUID;

/**
 * Caller of a request, taken from a valid access token: the {@code sub} claim (a UUID) and the
 * {@code role} claim (null when the token carries none or an unknown one).
 */
public record AuthenticatedUser(UUID id, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
