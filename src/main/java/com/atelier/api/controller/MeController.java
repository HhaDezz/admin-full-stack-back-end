package com.atelier.api.controller;

import com.atelier.api.dto.NotificationSettings;
import com.atelier.api.dto.request.account.CodeRequest;
import com.atelier.api.dto.request.account.PasswordConfirmRequest;
import com.atelier.api.dto.request.update.PasswordChangeRequest;
import com.atelier.api.dto.request.update.ProfileUpdateRequest;
import com.atelier.api.dto.response.ActivityResponse;
import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.LoginResponse;
import com.atelier.api.dto.response.RecoveryCodesResponse;
import com.atelier.api.dto.response.SecurityOverviewResponse;
import com.atelier.api.dto.response.SessionResponse;
import com.atelier.api.dto.response.TwoFactorSetupResponse;
import com.atelier.api.entity.User;
import com.atelier.api.security.AuthService;
import com.atelier.api.security.JwtUtil;
import com.atelier.api.security.SessionService;
import com.atelier.api.service.AccountService;
import com.atelier.api.service.AuditContext;
import com.atelier.api.service.UserService;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** The signed-in user's own account. Available to every signed-in user, no permission needed. */
@RestController
@RequestMapping("/me")
public class MeController {

    private final UserService users;
    private final AuthService auth;
    private final AccountService account;
    private final SessionService sessions;
    private final JwtUtil jwt;

    public MeController(UserService users, AuthService auth, AccountService account, SessionService sessions, JwtUtil jwt) {
        this.users = users;
        this.auth = auth;
        this.account = account;
        this.sessions = sessions;
        this.jwt = jwt;
    }

    /** Current roles and permissions, re-read from the database (the UI polls this). */
    @GetMapping
    public ApiResponse<LoginResponse> me() {
        User user = users.me();
        return ApiResponse.ok(LoginResponse.of(user, auth.effectivePermissions(user), null, null));
    }

    @PutMapping
    public ApiResponse<LoginResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest body) {
        User user = users.updateProfile(body);
        return ApiResponse.ok("Profile updated", LoginResponse.of(user, auth.effectivePermissions(user), null, null));
    }

    /** Changes the password, signs out every device and returns a fresh token for this one. */
    @PutMapping("/password")
    public ApiResponse<LoginResponse> changePassword(@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest http) {
        User user = users.changePassword(body);
        sessions.revokeAll(user.getId(), null);
        String token = sessions.start(user, AuditContext.clientIp(http), http.getHeader("User-Agent")).token();
        return ApiResponse.ok("Password changed",
                LoginResponse.of(user, auth.effectivePermissions(user), token, jwt.expirationSeconds()));
    }

    /* ── Security ── */

    @GetMapping("/security")
    public ApiResponse<SecurityOverviewResponse> security() {
        return ApiResponse.ok(account.overview());
    }

    /** Unlocks the lock screen (the token is still valid; this proves the person at the keyboard). */
    @PostMapping("/verify-password")
    public ApiResponse<Void> verifyPassword(@Valid @RequestBody PasswordConfirmRequest body, HttpServletRequest http) {
        account.verifyPassword(body.password(), AuditContext.clientIp(http));
        return ApiResponse.ok("Unlocked", null);
    }

    @PostMapping("/2fa/setup")
    public ApiResponse<TwoFactorSetupResponse> startTwoFactor() {
        return ApiResponse.ok("Scan the code with your authenticator app", account.startTwoFactor());
    }

    @PostMapping("/2fa/enable")
    public ApiResponse<RecoveryCodesResponse> enableTwoFactor(@Valid @RequestBody CodeRequest body) {
        return ApiResponse.ok("Two-step sign-in is on", account.enableTwoFactor(body.code()));
    }

    @PostMapping("/2fa/disable")
    public ApiResponse<Void> disableTwoFactor(@Valid @RequestBody PasswordConfirmRequest body, HttpServletRequest http) {
        account.disableTwoFactor(body.password(), AuditContext.clientIp(http));
        return ApiResponse.ok("Two-step sign-in is off", null);
    }

    @PostMapping("/2fa/recovery-codes")
    public ApiResponse<RecoveryCodesResponse> recoveryCodes(@Valid @RequestBody PasswordConfirmRequest body, HttpServletRequest http) {
        return ApiResponse.ok("New recovery codes created", account.regenerateRecoveryCodes(body.password(), AuditContext.clientIp(http)));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionResponse>> sessions() {
        return ApiResponse.ok(account.sessions());
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable String id) {
        account.revokeSession(id);
        return ApiResponse.ok("Device signed out", null);
    }

    /** Signs out every device except this one. */
    @DeleteMapping("/sessions")
    public ApiResponse<Map<String, Integer>> revokeOtherSessions() {
        int n = account.revokeOtherSessions();
        return ApiResponse.ok(n == 0 ? "No other devices were signed in" : "Signed out " + n + " other device" + (n == 1 ? "" : "s"),
                Map.of("revoked", n));
    }

    /* ── Notifications and activity ── */

    @GetMapping("/notification-settings")
    public ApiResponse<NotificationSettings> notificationSettings() {
        return ApiResponse.ok(account.notificationSettings());
    }

    @PutMapping("/notification-settings")
    public ApiResponse<NotificationSettings> updateNotificationSettings(@Valid @RequestBody NotificationSettings body) {
        return ApiResponse.ok("Notification settings saved", account.updateNotificationSettings(body));
    }

    @GetMapping("/activity")
    public ApiResponse<List<ActivityResponse>> activity(@RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(account.activity(limit));
    }
}
