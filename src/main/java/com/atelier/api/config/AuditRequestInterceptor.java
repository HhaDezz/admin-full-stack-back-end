package com.atelier.api.config;

import com.atelier.api.entity.AuditLog;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.service.AuditContext;
import com.atelier.api.service.AuditWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Layer 1 of the audit log: one row for every API call that changes something.
 *
 * A HandlerInterceptor wraps every controller method:
 *
 *   preHandle        runs before the controller  -> start the clock, give the request an id
 *   (controller runs, services run, the transaction commits or rolls back)
 *   afterCompletion  runs after the response is decided -> write the audit row
 *
 * Because it sits around ALL controllers, a brand-new endpoint is audited the
 * day it is written, with no extra code. The price is that it only knows the
 * outside of the call — who, which URL, what status — not what it meant.
 * That is what the DATA and BUSINESS rows add.
 *
 * Not recorded here:
 *   - GET requests: reading is not changing, and logging every page view would
 *     bury the useful rows.
 *   - /auth/**: sign-in and sign-out get their own, clearer AUTH rows.
 *   - Request bodies: they can contain passwords and card details.
 *   - Calls rejected by Spring Security before reaching a controller (no token,
 *     no permission): those never get this far.
 */
@Component
public class AuditRequestInterceptor implements HandlerInterceptor {

    private static final String START_ATTRIBUTE = "audit.startNanos";

    private final AuditWriter auditWriter;

    public AuditRequestInterceptor(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Set for every request (GETs too), so DATA and BUSINESS rows written
        // while it runs can all carry the same id.
        if (request.getAttribute(AuditContext.REQUEST_ID_ATTRIBUTE) == null) {
            request.setAttribute(AuditContext.REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString());
            request.setAttribute(START_ATTRIBUTE, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            if (!isChange(method)) return;

            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            if (path.startsWith("/auth/") || path.startsWith("/error")) return;

            int status = response.getStatus();
            Object started = request.getAttribute(START_ATTRIBUTE);
            Long durationMs = started instanceof Long nanos ? (System.nanoTime() - nanos) / 1_000_000 : null;

            AuditLog log = new AuditLog();
            log.setCategory(AuditLog.CATEGORY_REQUEST);
            log.setAction(actionFor(method, path));
            log.setEntityName(moduleOf(path));
            log.setEntityId(firstNumber(path));
            log.setPerformedBy(AuditContext.currentActor());
            log.setPerformedAt(LocalDateTime.now());
            log.setHttpMethod(method);
            log.setRequestPath(AuditContext.truncate(path, 500));
            log.setStatusCode(status);
            log.setDurationMs(durationMs);
            log.setDetails(method + " " + path + (status >= 400 ? " failed with status " + status : ""));
            AuditContext.stampRequest(log);

            auditWriter.writeAsync(log);
        } catch (Exception ignored) {
            // never let auditing break a request
        }
    }

    private static boolean isChange(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    /** A best guess from the URL; the BUSINESS row, where one exists, is the precise one. */
    private static AuditAction actionFor(String method, String path) {
        String p = path.toLowerCase();
        if ("DELETE".equals(method)) return AuditAction.DELETE;
        if ("PUT".equals(method) || "PATCH".equals(method)) return AuditAction.UPDATE;
        // POST /things creates; POST /things/12/issue acts on an existing one.
        return firstNumber(path) == null ? AuditAction.CREATE : AuditAction.UPDATE;
    }

    /** "/users/12/status" -> "users". */
    private static String moduleOf(String path) {
        for (String part : path.split("/")) {
            if (!part.isBlank()) return AuditContext.truncate(part, 100);
        }
        return "root";
    }

    /** "/users/12/status" -> 12. */
    private static Long firstNumber(String path) {
        for (String part : path.split("/")) {
            if (!part.isEmpty() && part.length() <= 18 && part.chars().allMatch(Character::isDigit)) {
                try {
                    return Long.parseLong(part);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
