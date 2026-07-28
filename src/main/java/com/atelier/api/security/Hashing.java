package com.atelier.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** SHA-256 hashing and random tokens for one-time links, recovery codes and revoked tokens. */
public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A URL-safe random token with the given number of random bytes (32 → 43 characters). */
    public static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** A recovery code like "7KQ2-M9XD": 8 characters without look-alikes (0/O, 1/I/L). */
    public static String recoveryCode() {
        String alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Normalises a typed recovery code: upper case, no spaces or dashes. */
    public static String normaliseCode(String code) {
        return code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
