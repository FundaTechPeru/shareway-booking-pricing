package com.fundatechperu.shareway.booking.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes of the boarding credentials (Sprint 2). bookings keeps ONLY these hashes:
 * - pin_hash: BCrypt of the 6-digit PIN (salted, verifiable);
 * - qr_token_hash: SHA-256 of the QR token (the token is already random and high-entropy,
 *   so a plain digest is enough and stays deterministic for lookups).
 * The clear values are published once in BookingConfirmed and never logged.
 */
@Component
public class BoardingCodeHasher {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public String hashPin(String pin) {
        return bcrypt.encode(pin);
    }

    public boolean matchesPin(String pin, String pinHash) {
        return pin != null && pinHash != null && bcrypt.matches(pin, pinHash);
    }

    public String hashQrToken(String qrToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(qrToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) hash.append(String.format("%02x", value));
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", exception);
        }
    }
}
