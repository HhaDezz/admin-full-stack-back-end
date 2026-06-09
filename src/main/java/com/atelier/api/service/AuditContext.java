package com.atelier.api.service;

import com.atelier.api.entity.AuditLog;
import com.atelier.api.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Answers "who is doing this, and from where?" for the current thread.
 *
 * Both Spring Security and Spring MVC keep the current user / current request
 * in thread-local storage, so this only works on the thread that is handling
 * the request. That is why every audit record is filled in HERE, on the
 * request thread, and only the database write is handed to a background
 * thread (AuditWriter) — the background thread has no user and no request.
 */
public final class AuditContext {

    /** Request attribute holding the id shared by every audit row of one API call. */
    public static final String REQUEST_ID_ATTRIBUTE = "audit.requestId";

    public static final String SYSTEM_ACTOR = "system";

    private AuditContext() {
    }

    /** The signed-in user's email, or "system" for scheduled jobs and anonymous calls. */
    public static String currentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return SYSTEM_ACTOR;
            String name = auth.getName();
            if (name == null || name.isBlank() || "anonymousUser".equals(name)) return SYSTEM_ACTOR;
            return name;
        } catch (Exception e) {
            return SYSTEM_ACTOR;
        }
    }

    /** The request being handled on this thread, or null outside a request (e.g. a scheduler). */
    public static HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                return servletAttrs.getRequest();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /**
     * The caller's IP. Behind a reverse proxy (nginx) the socket address is the
     * proxy's, and the real client is the first entry of X-Forwarded-For.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    /** Copies request id, IP and browser onto a record, if there is a current request. */
    public static void stampRequest(AuditLog log) {
        HttpServletRequest request = currentRequest();
        if (request == null) return;
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId != null) log.setRequestId(requestId.toString());
        log.setIpAddress(clientIp(request));
        log.setUserAgent(truncate(request.getHeader("User-Agent"), 300));
    }

    public static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
