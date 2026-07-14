package com.atelier.api.dto.response;

import com.atelier.api.entity.AuditLog;

import java.time.LocalDateTime;

/** One line of "My activity": a sign-in, a security change or something the user changed. */
public record ActivityResponse(Long id, LocalDateTime at, String category, String action, String entityName,
                               String details, String ipAddress, String browser, String os) {

    public static ActivityResponse of(AuditLog a) {
        String ua = a.getUserAgent() == null ? "" : a.getUserAgent();
        return new ActivityResponse(a.getId(), a.getPerformedAt(), a.getCategory(), a.getAction().name(),
                a.getEntityName(), a.getDetails(), a.getIpAddress(), SessionResponse.browser(ua), SessionResponse.os(ua));
    }
}
