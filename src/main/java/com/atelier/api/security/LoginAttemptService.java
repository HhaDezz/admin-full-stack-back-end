package com.atelier.api.security;

import com.atelier.api.config.AppProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


/**
 * Slows down password guessing: after too many failed sign-ins for the same email from
 * the same IP within the lock-out window, further attempts are refused until it passes.
 * Kept in memory (resets on restart); for several API instances, move it to a shared store.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(AppProperties props) {
        this.maxAttempts = Math.max(1, props.security().maxLoginAttempts());
        this.window = props.security().loginLockout();
    }

    private static String key(String email, String ip) {
        return (email == null ? "" : email.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** Seconds until the caller may try again, or 0 if not locked. */
    public long lockedForSeconds(String email, String ip) {
        Deque<Instant> list = failures.get(key(email, ip));
        if (list == null) return 0;
        synchronized (list) {
            prune(list);
            if (list.size() < maxAttempts) return 0;
            Instant unlock = list.peekFirst().plus(window);
            return Math.max(1, Duration.between(Instant.now(), unlock).toSeconds());
        }
    }

    public void recordFailure(String email, String ip) {
        Deque<Instant> list = failures.computeIfAbsent(key(email, ip), k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list);
            list.addLast(Instant.now());
        }
    }

    public void recordSuccess(String email, String ip) {
        failures.remove(key(email, ip));
    }

    private void prune(Deque<Instant> list) {
        Instant cutoff = Instant.now().minus(window);
        while (!list.isEmpty() && list.peekFirst().isBefore(cutoff)) list.pollFirst();
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanUp() {
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue());
                return e.getValue().isEmpty();
            }
        });
    }
}
