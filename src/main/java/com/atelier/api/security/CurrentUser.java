package com.atelier.api.security;

import com.atelier.api.exception.BusinessException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.http.HttpStatus;

/** Static access to the signed-in user for services. */
public final class CurrentUser {

    /** Authority held only by super admins. It can't be granted through roles. */
    public static final String SYSTEM_AUTHORITY = "SYSTEM_USER";

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) return user;
        throw new BusinessException("Not signed in", HttpStatus.UNAUTHORIZED);
    }

    public static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user && user.superAdmin();
    }
}
