package com.atelier.api.controller;

import com.atelier.api.dto.request.LoginRequest;
import com.atelier.api.dto.request.account.EmailRequest;
import com.atelier.api.dto.request.account.RegisterRequest;
import com.atelier.api.dto.request.account.ResetPasswordRequest;
import com.atelier.api.dto.request.account.TokenRequest;
import com.atelier.api.dto.request.account.TwoFactorLoginRequest;
import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.AuthOptionsResponse;
import com.atelier.api.dto.response.LoginResponse;
import com.atelier.api.security.AuthService;
import com.atelier.api.security.AuthUser;
import com.atelier.api.service.AuditContext;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Sign-in, sign-out, sign-up, email confirmation and password reset. All public except logout. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /** Same answer whether or not the email has an account, so the endpoint can't be used to find accounts. */
    private static final String CHECK_EMAIL = "If an account exists for that email, we've sent a link to it.";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/options")
    public ApiResponse<AuthOptionsResponse> options() {
        return ApiResponse.ok(auth.options());
    }

    /** Signs in, or answers twoFactorRequired with a challengeToken when a code is needed. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResponse r = auth.login(request, AuditContext.clientIp(http), http.getHeader("User-Agent"));
        return ApiResponse.ok(r.twoFactorRequired() ? "Enter your code" : "Signed in", r);
    }

    @PostMapping("/login/2fa")
    public ApiResponse<LoginResponse> loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest body, HttpServletRequest http) {
        return ApiResponse.ok("Signed in",
                auth.completeTwoFactor(body.challengeToken(), body.code(), AuditContext.clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String header) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String sessionId = a != null && a.getPrincipal() instanceof AuthUser u ? u.sessionId() : null;
        if (header.startsWith("Bearer ")) auth.logout(header.substring(7).trim(), sessionId);
        return ApiResponse.ok("Signed out", null);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthService.Registered> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest http) {
        AuthService.Registered r = auth.register(body, AuditContext.clientIp(http));
        return ApiResponse.ok(r.verificationRequired()
                ? "Account created. Check your email for a link to confirm your address."
                : "Account created. You can sign in now.", r);
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody TokenRequest body) {
        auth.verifyEmail(body.token());
        return ApiResponse.ok("Email confirmed. You can sign in now.", null);
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody EmailRequest body, HttpServletRequest http) {
        auth.resendVerification(body.email(), AuditContext.clientIp(http));
        return ApiResponse.ok(CHECK_EMAIL, null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest body, HttpServletRequest http) {
        auth.forgotPassword(body.email(), AuditContext.clientIp(http));
        return ApiResponse.ok(CHECK_EMAIL, null);
    }

    /** Lets the reset page tell the user straight away when a link is stale. */
    @PostMapping("/reset-password/check")
    public ApiResponse<Void> checkResetLink(@Valid @RequestBody TokenRequest body) {
        auth.checkResetToken(body.token());
        return ApiResponse.ok("Link is valid", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest http) {
        auth.resetPassword(body.token(), body.password(), AuditContext.clientIp(http));
        return ApiResponse.ok("Password changed. Sign in with your new password.", null);
    }
}
