package com.atelier.api.security;

import com.atelier.api.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

import org.springframework.stereotype.Service;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies signed (HS256) access tokens. The subject is the user id. */
@Service
public class JwtUtil {

    private final Key key;
    private final long expirationMs;

    public JwtUtil(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be at least 32 characters. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.jwt().expiration().toMillis();
    }

    /** Claim that marks a short-lived token for the second sign-in step; never accepted as an access token. */
    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_TWO_FACTOR = "2fa";
    private static final long CHALLENGE_MS = 5 * 60 * 1000;

    /** An access token for one session (the session id is the token's jti). */
    public String issue(Long userId, String email, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(sessionId)
                .claim("email", email)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Proof that the password was right, valid for 5 minutes, exchanged for a session once the code is checked. */
    public String issueChallenge(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(PURPOSE_CLAIM, PURPOSE_TWO_FACTOR)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + CHALLENGE_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** User id from a valid, unexpired two-step challenge token, or null. */
    public Long challengeUserId(String token) {
        Claims c = parse(token);
        if (c == null || !PURPOSE_TWO_FACTOR.equals(c.get(PURPOSE_CLAIM, String.class))) return null;
        try {
            return Long.valueOf(c.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns the verified claims, or null when the token is malformed, tampered with or expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public java.time.Duration expiration() {
        return java.time.Duration.ofMillis(expirationMs);
    }

    public long expirationSeconds() {
        return expirationMs / 1000;
    }

    public static Instant issuedAt(Claims claims) {
        return claims.getIssuedAt() == null ? Instant.EPOCH : claims.getIssuedAt().toInstant();
    }
}
