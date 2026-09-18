package com.atelier.api.dto.response;

import com.atelier.api.entity.UserSession;

import java.time.LocalDateTime;

/** A signed-in device, with the browser and OS read from its user agent. */
public record SessionResponse(String id, String browser, String os, String device, String ipAddress,
                              LocalDateTime createdAt, LocalDateTime lastSeenAt, boolean current) {

    public static SessionResponse of(UserSession s, String currentId) {
        String ua = s.getUserAgent() == null ? "" : s.getUserAgent();
        return new SessionResponse(s.getId(), browser(ua), os(ua), device(ua), s.getIpAddress(), s.getCreatedAt(),
                s.getLastSeenAt(), s.getId().equals(currentId));
    }

    static String browser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        if (ua.contains("PostmanRuntime")) return "Postman";
        if (ua.contains("curl/")) return "curl";
        return ua.isBlank() ? "Unknown browser" : "Other browser";
    }

    static String os(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return "Unknown OS";
    }

    static String device(String ua) {
        if (ua.contains("iPad") || ua.contains("Tablet")) return "tablet";
        if (ua.contains("Mobile") || ua.contains("iPhone") || ua.contains("Android")) return "phone";
        return "desktop";
    }
}
