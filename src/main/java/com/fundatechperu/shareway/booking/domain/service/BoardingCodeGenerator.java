package com.fundatechperu.shareway.booking.domain.service;

import java.security.SecureRandom;

/**
 * Generates the boarding credentials of a confirmed booking: a 6-digit PIN (leading zeros
 * allowed) and a random QR token. No Spring or JPA: the bean wiring lives in
 * infrastructure. Only the caller of the domain stores their hashes; this class never
 * persists or logs the clear values.
 */
public class BoardingCodeGenerator {

    private static final int PIN_MODULUS = 1_000_000;
    private static final int QR_TOKEN_BYTES = 16;   // 128 bits -> 32 hex characters

    private final SecureRandom random = new SecureRandom();

    /** 6-digit PIN as text so leading zeros are preserved ("004271"). */
    public String pin() {
        return String.format("%06d", random.nextInt(PIN_MODULUS));
    }

    /** 32-character lowercase hexadecimal token, unique enough to be scanned once. */
    public String qrToken() {
        byte[] bytes = new byte[QR_TOKEN_BYTES];
        random.nextBytes(bytes);
        StringBuilder token = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) token.append(String.format("%02x", value));
        return token.toString();
    }
}
