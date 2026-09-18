package com.atelier.api.security;

import com.atelier.api.entity.User;
import com.atelier.api.entity.UserSession;
import com.atelier.api.repository.UserSessionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed-in devices. Every access token belongs to a session row; revoking the row signs
 * that device out on its next request. "Last active" is written at most once a minute per session.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final long TOUCH_EVERY_MS = 60_000;

    private final UserSessionRepository sessions;
    private final JwtUtil jwt;
    private final Map<String, Long> lastTouch = new ConcurrentHashMap<>();

    public SessionService(UserSessionRepository sessions, JwtUtil jwt) {
        this.sessions = sessions;
        this.jwt = jwt;
    }

    public record Issued(String token, UserSession session) {
    }

    /** Starts a session and returns its access token. */
    @Transactional
    public Issued start(User user, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        UserSession s = new UserSession();
        s.setId(UUID.randomUUID().toString());
        s.setUserId(user.getId());
        s.setIpAddress(truncate(ip, 64));
        s.setUserAgent(truncate(userAgent, 400));
        s.setCreatedAt(now);
        s.setLastSeenAt(now);
        s.setExpiresAt(now.plus(jwt.expiration()));
        sessions.save(s);
        return new Issued(jwt.issue(user.getId(), user.getEmail(), s.getId()), s);
    }

    /** Is this session still signed in? Also records activity (throttled). */
    @Transactional
    public boolean validate(String sessionId) {
        UserSession s = sessions.findById(sessionId).orElse(null);
        if (s == null || !s.isActive()) {
            lastTouch.remove(sessionId);
            return false;
        }
        long nowMs = System.currentTimeMillis();
        Long prev = lastTouch.get(sessionId);
        if (prev == null || nowMs - prev > TOUCH_EVERY_MS) {
            lastTouch.put(sessionId, nowMs);
            sessions.touch(sessionId, LocalDateTime.now());
        }
        return true;
    }

    /** Has this user signed in from this browser before? (Used for "new sign-in" alerts.) */
    @Transactional(readOnly = true)
    public boolean isKnownDevice(Long userId, String userAgent) {
        return userAgent != null && sessions.existsByUserIdAndUserAgent(userId, truncate(userAgent, 400));
    }

    @Transactional(readOnly = true)
    public List<UserSession> active(Long userId) {
        return sessions.findActive(userId, LocalDateTime.now());
    }

    /** Revokes one of the user's sessions. Returns false if it isn't theirs or is already gone. */
    @Transactional
    public boolean revoke(Long userId, String sessionId) {
        UserSession s = sessions.findById(sessionId).orElse(null);
        if (s == null || !s.getUserId().equals(userId) || s.getRevokedAt() != null) return false;
        s.setRevokedAt(LocalDateTime.now());
        lastTouch.remove(sessionId);
        return true;
    }

    /** Signs out every other device (keep = the current session), or all devices when keep is null. */
    @Transactional
    public int revokeAll(Long userId, String keep) {
        return sessions.revokeAll(userId, keep == null ? "" : keep, LocalDateTime.now());
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purge() {
        int n = sessions.deleteOld(LocalDateTime.now().minusDays(30));
        if (n > 0) log.info("Removed {} old sessions", n);
        lastTouch.clear();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
