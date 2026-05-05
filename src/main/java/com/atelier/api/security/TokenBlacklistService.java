package com.atelier.api.security;

import com.atelier.api.entity.RevokedToken;
import com.atelier.api.repository.RevokedTokenRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;

/**
 * Sign-out for stateless tokens: a signed-out token is remembered (hashed) in the
 * database until it expires. No Redis or other extra service is needed.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private final RevokedTokenRepository repository;

    public TokenBlacklistService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    public void revoke(String token, Claims claims) {
        LocalDateTime expires = claims.getExpiration() == null
                ? LocalDateTime.now().plusDays(1)
                : LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault());
        repository.save(new RevokedToken(hash(token), expires));
    }

    public boolean isRevoked(String token) {
        return repository.existsById(hash(token));
    }

    /** Hourly clean-up of entries whose tokens have expired anyway. */
    @Scheduled(cron = "0 7 * * * *")
    public void purgeExpired() {
        int removed = repository.deleteExpired(LocalDateTime.now());
        if (removed > 0) log.info("Removed {} expired revoked-token entries", removed);
    }

    static String hash(String token) {
        return Hashing.sha256(token);
    }
}
