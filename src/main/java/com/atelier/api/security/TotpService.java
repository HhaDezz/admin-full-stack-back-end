package com.atelier.api.security;

import com.atelier.api.config.AppProperties;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * Time-based one-time passwords (RFC 6238): the six-digit codes from Google Authenticator,
 * 1Password, Authy and similar apps. SHA-1, 30-second steps, one step of clock drift allowed.
 * No library needed.
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final String issuer;

    public TotpService(AppProperties props) {
        this.issuer = props.brandName();
    }

    /** A new random secret (160 bits), Base32-encoded as authenticator apps expect. */
    public String newSecret() {
        return base32(Hashing.randomBytes(20));
    }

    /** The otpauth:// link that authenticator apps read from a QR code. */
    public String otpauthUrl(String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Checks a code against the current, previous and next time step. Returns the matching step,
     * or -1. Steps at or before lastUsedStep are refused, so a code can't be replayed.
     */
    public long verify(String secret, String code, Long lastUsedStep) {
        if (secret == null || code == null) return -1;
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) return -1;
        long now = System.currentTimeMillis() / 1000 / STEP_SECONDS;
        byte[] key = unbase32(secret);
        for (long step = now - 1; step <= now + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) continue;
            if (constantTimeEquals(codeAt(key, step), digits)) return step;
        }
        return -1;
    }

    /** The code for a time step. Public for tests. */
    public String codeAt(String secret, long step) {
        return codeAt(unbase32(secret), step);
    }

    public static long currentStep() {
        return System.currentTimeMillis() / 1000 / STEP_SECONDS;
    }

    private static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", bin % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String clean = s.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
