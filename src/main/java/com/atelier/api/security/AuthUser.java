package com.atelier.api.security;

/**
 * The signed-in user, as stored in the security context for the current request.
 * sessionId is the token's jti (null for tokens issued before sessions existed).
 */
public record AuthUser(Long id, String email, boolean superAdmin, String sessionId) {

    /** Used by the audit log and by Spring Security's getName(). */
    @Override
    public String toString() {
        return email;
    }
}
