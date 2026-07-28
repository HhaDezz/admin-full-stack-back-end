package com.atelier.api.security;

import com.atelier.api.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Simple in-memory limits for endpoints that send email or accept guesses
 * (sign-up, password reset, resend verification, two-step codes).
 */
@Service
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final boolean enabled;

    /** app.security.rate-limits=false turns the limits off (tests). Keep them on in production. */
    public RateLimiter(@Value("${app.security.rate-limits:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Throws 429 when the key was used more than max times within the window. */
    public void check(String key, int max, Duration window) {
        if (!enabled) return;
        Deque<Instant> list = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            Instant cutoff = Instant.now().minus(window);
            while (!list.isEmpty() && list.peekFirst().isBefore(cutoff)) list.pollFirst();
            if (list.size() >= max) {
                long wait = Math.max(1, Duration.between(Instant.now(), list.peekFirst().plus(window)).toMinutes() + 1);
                throw new BusinessException("Too many requests. Try again in " + wait + " minute(s).", HttpStatus.TOO_MANY_REQUESTS);
            }
            list.addLast(Instant.now());
        }
    }

    @Scheduled(fixedDelay = 900_000)
    public void cleanUp() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(2));
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
