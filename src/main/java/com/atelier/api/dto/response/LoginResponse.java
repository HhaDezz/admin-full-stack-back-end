package com.atelier.api.dto.response;

import com.atelier.api.entity.User;

import java.util.List;


/**
 * Who is signed in and what they may do. Returned by sign-in (with a token) and by GET /me
 * (token null), so the UI can refresh permissions without signing out.
 */
public record LoginResponse(
        String token,
        Long expiresIn,
        Long id,
        String username,
        String name,
        String email,
        String phone,
        String address,
        /* First role name, for display. */
        String role,
        List<String> roles,
        List<String> permissions,
        boolean superAdmin,
        boolean emailVerified,
        boolean twoFactorEnabled,
        /* True when the password was right but a code is still needed: send challengeToken to /auth/login/2fa. */
        boolean twoFactorRequired,
        String challengeToken) {

    public static LoginResponse of(User user, List<String> permissions, String token, Long expiresIn) {
        List<String> roles = user.getRoles().stream().map(r -> r.getName()).sorted().toList();
        String primary = user.getRoles().stream()
                .sorted((a, b) -> Boolean.compare(b.isSystem(), a.isSystem()))
                .map(r -> r.getName()).findFirst().orElse("");
        return new LoginResponse(token, expiresIn, user.getId(), user.getEmail(), user.getName(), user.getEmail(),
                user.getPhone(), user.getAddress(), primary, roles, permissions, user.isSuperAdmin(),
                user.isEmailVerified(), user.isTwoFactorEnabled(), false, null);
    }

    /** First step done; the client asks for the code and posts it with this token. */
    public static LoginResponse challenge(String challengeToken) {
        return new LoginResponse(null, null, null, null, null, null, null, null, null, List.of(), List.of(), false,
                true, true, true, challengeToken);
    }
}
