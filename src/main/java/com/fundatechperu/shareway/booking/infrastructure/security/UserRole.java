package com.fundatechperu.shareway.booking.infrastructure.security;

import java.util.Locale;
import java.util.Optional;

/** Roles carried by the {@code role} claim of the access tokens. */
public enum UserRole {
    PASSENGER,
    DRIVER,
    ADMIN;

    static Optional<UserRole> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
