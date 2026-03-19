package com.atelier.api.security;

import com.atelier.api.entity.User;
import com.atelier.api.repository.UserRepository;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;


import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads "Authorization: Bearer &lt;token&gt;", verifies it and loads the user's current
 * roles and permissions from the database on every request, so permission changes,
 * disabled accounts and password changes take effect immediately.
 *
 * An invalid token doesn't fail here; the request simply stays anonymous and the
 * entry point answers 401 with the reason stored under {@link #ERROR_ATTRIBUTE}.
 */
public class JwtFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = "auth.error";

    private final JwtUtil jwtService;
    private final TokenBlacklistService revocation;
    private final SessionService sessions;
    private final UserRepository users;
    private final CustomUserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtService, TokenBlacklistService revocation, SessionService sessions,
                     UserRepository users, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.revocation = revocation;
        this.sessions = sessions;
        this.users = users;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authenticate(request, header.substring(7).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        Claims claims = jwtService.parse(token);
        if (claims == null || claims.get(JwtUtil.PURPOSE_CLAIM) != null || revocation.isRevoked(token)) {
            request.setAttribute(ERROR_ATTRIBUTE, "Your session has expired. Please sign in again.");
            return;
        }
        String sessionId = claims.getId();
        if (sessionId != null && !sessions.validate(sessionId)) {
            request.setAttribute(ERROR_ATTRIBUTE, "You were signed out of this device. Please sign in again.");
            return;
        }
        Optional<User> found;
        try {
            found = users.findById(Long.valueOf(claims.getSubject()));
        } catch (NumberFormatException e) {
            found = Optional.empty();
        }
        if (found.isEmpty()) {
            request.setAttribute(ERROR_ATTRIBUTE, "Your session has expired. Please sign in again.");
            return;
        }
        User user = found.get();
        if (!user.isEnabled()) {
            request.setAttribute(ERROR_ATTRIBUTE, "This account has been disabled.");
            return;
        }
        if (user.getCredentialsChangedAt() != null
                && JwtUtil.issuedAt(claims).isBefore(user.getCredentialsChangedAt().atZone(ZoneId.systemDefault()).toInstant().minusSeconds(1))) {
            request.setAttribute(ERROR_ATTRIBUTE, "Your password was changed. Please sign in again.");
            return;
        }

        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.isSuperAdmin(), sessionId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, userDetailsService.authoritiesFor(user)));
    }
}
