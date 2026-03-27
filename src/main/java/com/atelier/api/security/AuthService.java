package com.atelier.api.security;

import com.atelier.api.config.AppProperties;
import com.atelier.api.dto.request.LoginRequest;
import com.atelier.api.dto.request.account.RegisterRequest;
import com.atelier.api.dto.response.AuthOptionsResponse;
import com.atelier.api.dto.response.LoginResponse;
import com.atelier.api.entity.Permission;
import com.atelier.api.entity.RecoveryCode;
import com.atelier.api.entity.User;
import com.atelier.api.entity.UserToken;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.enums.TokenPurpose;
import com.atelier.api.exception.BusinessException;
import com.atelier.api.repository.PermissionRepository;
import com.atelier.api.repository.RecoveryCodeRepository;
import com.atelier.api.repository.RoleRepository;
import com.atelier.api.repository.UserRepository;
import com.atelier.api.repository.UserTokenRepository;
import com.atelier.api.service.AuditLogService;
import com.atelier.api.service.MailService;
import com.atelier.api.service.NotificationService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;

/**
 * Everything that happens before someone is signed in: sign-in (with the optional second
 * step), sign-out, sign-up, email verification and password reset.
 */
@Service
public class AuthService {

    /** Same message for unknown email and wrong password, so attackers can't discover accounts. */
    private static final String BAD_CREDENTIALS = "Incorrect email or password.";
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserTokenRepository tokens;
    private final RecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final TokenBlacklistService revocation;
    private final SessionService sessions;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final LoginAttemptService attempts;
    private final RateLimiter limiter;
    private final AuditLogService audit;
    private final MailService mail;
    private final NotificationService notifications;
    private final AppProperties props;
    /** Compared against when the email is unknown, so both paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, PermissionRepository permissions, UserTokenRepository tokens,
                       RecoveryCodeRepository recoveryCodes, PasswordEncoder encoder, JwtUtil jwt,
                       TokenBlacklistService revocation, SessionService sessions, TotpService totp, SecretCipher cipher,
                       LoginAttemptService attempts, RateLimiter limiter, AuditLogService audit, MailService mail,
                       NotificationService notifications, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.tokens = tokens;
        this.recoveryCodes = recoveryCodes;
        this.encoder = encoder;
        this.jwt = jwt;
        this.revocation = revocation;
        this.sessions = sessions;
        this.totp = totp;
        this.cipher = cipher;
        this.attempts = attempts;
        this.limiter = limiter;
        this.audit = audit;
        this.mail = mail;
        this.notifications = notifications;
        this.props = props;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password");
    }

    public AuthOptionsResponse options() {
        return new AuthOptionsResponse(props.registration().enabled(), props.registration().requireVerification(),
                props.brandName());
    }

    /* ── Sign-in ── */

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String email = request.email().trim().toLowerCase();
        long wait = attempts.lockedForSeconds(email, ip);
        if (wait > 0) {
            audit.recordAuth(AuditAction.LOGIN_FAILED, email, "Sign-in blocked: too many failed attempts");
            throw new BusinessException("Too many failed attempts. Try again in " + Math.max(1, (wait + 59) / 60) + " minute(s).", HttpStatus.TOO_MANY_REQUESTS);
        }

        User user = users.findByEmail(email).orElse(null);
        boolean matches = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            attempts.recordFailure(email, ip);
            audit.recordAuth(AuditAction.LOGIN_FAILED, email,
                    user == null ? "Sign-in failed: unknown email" : "Sign-in failed: wrong password");
            throw new BusinessException(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        if (!user.isEnabled()) {
            audit.recordAuth(AuditAction.LOGIN_FAILED, email, "Sign-in refused: account disabled");
            throw new BusinessException("This account has been disabled. Contact an administrator.", HttpStatus.FORBIDDEN);
        }
        attempts.recordSuccess(email, ip);
        if (!user.isEmailVerified() && props.registration().requireVerification()) {
            audit.recordAuth(AuditAction.LOGIN_FAILED, email, "Sign-in refused: email not confirmed");
            throw BusinessException.withCode("Confirm your email address first. We sent you a link when you signed up.",
                    HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
        }
        if (user.isTwoFactorEnabled()) {
            audit.recordAuth(AuditAction.OTHER, email, "Password accepted; waiting for two-step code");
            return LoginResponse.challenge(jwt.issueChallenge(user.getId()));
        }
        return finishLogin(user, ip, userAgent, "Signed in");
    }

    /** The second step: a code from the authenticator app, or a recovery code. */
    @Transactional
    public LoginResponse completeTwoFactor(String challengeToken, String code, String ip, String userAgent) {
        Long userId = jwt.challengeUserId(challengeToken);
        User user = userId == null ? null : users.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || !user.isTwoFactorEnabled()) {
            throw BusinessException.withCode("Your sign-in timed out. Please sign in again.", HttpStatus.UNAUTHORIZED, "CHALLENGE_EXPIRED");
        }
        String key = "2fa:" + user.getEmail();
        long wait = attempts.lockedForSeconds(key, ip);
        if (wait > 0) {
            throw new BusinessException("Too many wrong codes. Try again in " + Math.max(1, (wait + 59) / 60) + " minute(s).", HttpStatus.TOO_MANY_REQUESTS);
        }
        String trimmed = code.replaceAll("\\s", "");
        String how;
        if (trimmed.matches("\\d{6}")) {
            long step = totp.verify(cipher.decrypt(user.getTotpSecret()), trimmed, user.getTotpLastStep());
            if (step < 0) throw wrongCode(user, key, ip, false);
            user.setTotpLastStep(step);
            how = "Signed in with two-step code";
        } else {
            RecoveryCode match = findRecoveryCode(user.getId(), trimmed);
            if (match == null) throw wrongCode(user, key, ip, true);
            // Count before marking it used: the query would flush the change first.
            long left = recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId()) - 1;
            match.setUsedAt(LocalDateTime.now());
            how = "Signed in with a recovery code";
            notifications.notify(user, NotificationService.SECURITY, "RECOVERY_CODE_USED", "A recovery code was used",
                    "You signed in with a recovery code. " + left + " of 10 left." + (left <= 3 ? " Create new codes soon." : ""),
                    "/settings/security");
        }
        attempts.recordSuccess(key, ip);
        return finishLogin(user, ip, userAgent, how);
    }

    private BusinessException wrongCode(User user, String key, String ip, boolean recovery) {
        attempts.recordFailure(key, ip);
        audit.recordAuth(AuditAction.LOGIN_FAILED, user.getEmail(), recovery ? "Sign-in failed: wrong recovery code" : "Sign-in failed: wrong two-step code");
        return BusinessException.badRequest(recovery
                ? "That recovery code didn't work. Each code works once; check for typos or try another."
                : "That code didn't work. Check the time on your phone and try again.", "code");
    }

    private RecoveryCode findRecoveryCode(Long userId, String typed) {
        String hash = Hashing.sha256(Hashing.normaliseCode(typed));
        return recoveryCodes.findByUserIdAndUsedAtIsNull(userId).stream()
                .filter(c -> c.getCodeHash().equals(hash)).findFirst().orElse(null);
    }

    private LoginResponse finishLogin(User user, String ip, String userAgent, String auditText) {
        boolean seenBefore = user.getLastLoginAt() != null;
        boolean knownDevice = sessions.isKnownDevice(user.getId(), userAgent);
        SessionService.Issued issued = sessions.start(user, ip, userAgent);
        user.setLastLoginAt(LocalDateTime.now());
        audit.recordAuth(AuditAction.LOGIN, user.getEmail(), auditText);
        if (seenBefore && !knownDevice) {
            var s = com.atelier.api.dto.response.SessionResponse.of(issued.session(), null);
            notifications.notify(user, NotificationService.SECURITY, "NEW_SIGN_IN", "New sign-in to your account",
                    s.browser() + " on " + s.os() + (ip == null ? "" : ", from " + ip) + ", " + LocalDateTime.now().format(WHEN) + ".",
                    "/settings/security");
        }
        return LoginResponse.of(user, effectivePermissions(user), issued.token(), jwt.expirationSeconds());
    }

    public void logout(String token, String sessionId) {
        Claims claims = jwt.parse(token);
        if (claims == null) return; // already invalid: nothing to revoke
        if (sessionId != null) {
            try {
                sessions.revoke(Long.valueOf(claims.getSubject()), sessionId);
            } catch (NumberFormatException ignored) {
                // malformed subject: fall through to the token blacklist
            }
        }
        revocation.revoke(token, claims);
        audit.recordAuth(AuditAction.LOGOUT, claims.get("email", String.class), "Signed out");
    }

    /* ── Sign-up and email verification ── */

    public record Registered(String email, boolean verificationRequired) {
    }

    @Transactional
    public Registered register(RegisterRequest req, String ip) {
        if (!props.registration().enabled()) {
            throw BusinessException.forbidden("Sign-up is turned off. Ask an administrator for an account.");
        }
        limiter.check("register:" + ip, 5, Duration.ofHours(1));
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw BusinessException.conflict("An account with this email already exists. Sign in or reset your password.", "email");
        }
        boolean verify = props.registration().requireVerification();
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setEnabled(true);
        if (!verify) user.setEmailVerifiedAt(LocalDateTime.now());
        roles.findByName(props.registration().defaultRole()).ifPresent(r -> user.setRoles(new HashSet<>(List.of(r))));
        users.save(user);
        audit.recordAuth(AuditAction.REGISTER, email, "Created an account");
        notifications.notify(user, NotificationService.PRODUCT, "WELCOME", "Welcome to " + props.brandName(),
                "Your account is ready. Set up two-step sign-in in Settings to keep it safe.", "/settings/security");
        if (verify) sendVerification(user);
        return new Registered(email, verify);
    }

    @Transactional
    public void verifyEmail(String token) {
        UserToken t = usableToken(token, TokenPurpose.VERIFY_EMAIL);
        User user = t.getUser();
        if (user.getEmailVerifiedAt() == null) user.setEmailVerifiedAt(LocalDateTime.now());
        t.setUsedAt(LocalDateTime.now());
        audit.recordAuth(AuditAction.EMAIL_VERIFIED, user.getEmail(), "Confirmed email address");
    }

    /** Always answers the same way, so it can't be used to discover accounts. */
    @Transactional
    public void resendVerification(String email, String ip) {
        limiter.check("resend:" + ip, 5, Duration.ofMinutes(15));
        users.findByEmail(email.trim().toLowerCase())
                .filter(u -> u.isEnabled() && !u.isEmailVerified())
                .ifPresent(u -> {
                    limiter.check("resend:" + u.getEmail(), 3, Duration.ofHours(1));
                    sendVerification(u);
                });
    }

    private void sendVerification(User user) {
        String raw = issueToken(user, TokenPurpose.VERIFY_EMAIL, VERIFY_TTL);
        mail.verifyEmail(user.getEmail(), user.getName(), props.webUrl() + "/verify-email?token=" + raw);
    }

    /* ── Password reset ── */

    /** Always answers the same way, so it can't be used to discover accounts. */
    @Transactional
    public void forgotPassword(String email, String ip) {
        limiter.check("forgot:" + ip, 5, Duration.ofMinutes(15));
        String normalised = email.trim().toLowerCase();
        users.findByEmail(normalised).filter(User::isEnabled).ifPresent(u -> {
            limiter.check("forgot:" + normalised, 3, Duration.ofHours(1));
            String raw = issueToken(u, TokenPurpose.RESET_PASSWORD, RESET_TTL);
            mail.resetPassword(u.getEmail(), u.getName(), props.webUrl() + "/reset-password?token=" + raw);
            audit.recordAuth(AuditAction.PASSWORD_RESET_REQUESTED, u.getEmail(), "Requested a password reset link");
        });
    }

    @Transactional
    public void resetPassword(String token, String password, String ip) {
        limiter.check("reset:" + ip, 10, Duration.ofMinutes(15));
        UserToken t = usableToken(token, TokenPurpose.RESET_PASSWORD);
        User user = t.getUser();
        if (!user.isEnabled()) throw BusinessException.forbidden("This account has been disabled. Contact an administrator.");
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordHash(encoder.encode(password));
        user.setCredentialsChangedAt(now);
        // Following the emailed link proves they own the address.
        if (user.getEmailVerifiedAt() == null) user.setEmailVerifiedAt(now);
        t.setUsedAt(now);
        sessions.revokeAll(user.getId(), null);
        attempts.recordSuccess(user.getEmail(), ip);
        audit.recordAuth(AuditAction.PASSWORD_RESET, user.getEmail(), "Reset password with an emailed link");
        notifications.notify(user, NotificationService.SECURITY, "PASSWORD_CHANGED", "Your password was reset",
                "Your password was reset with an emailed link and every device was signed out.", "/settings/security");
    }

    /** Can this link still be used? (Lets the reset page say so before the user types a password.) */
    @Transactional(readOnly = true)
    public void checkResetToken(String token) {
        usableToken(token, TokenPurpose.RESET_PASSWORD);
    }

    private String issueToken(User user, TokenPurpose purpose, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        tokens.retireOpen(user.getId(), purpose, now);
        String raw = Hashing.randomToken(32);
        UserToken t = new UserToken();
        t.setUser(user);
        t.setPurpose(purpose);
        t.setTokenHash(Hashing.sha256(raw));
        t.setExpiresAt(now.plus(ttl));
        tokens.save(t);
        return raw;
    }

    private UserToken usableToken(String raw, TokenPurpose purpose) {
        UserToken t = raw == null ? null : tokens.findByTokenHashAndPurpose(Hashing.sha256(raw.trim()), purpose).orElse(null);
        if (t == null || !t.isUsable()) {
            throw BusinessException.withCode(purpose == TokenPurpose.RESET_PASSWORD
                    ? "This reset link has expired or was already used. Ask for a new one."
                    : "This confirmation link has expired or was already used. Sign in to get a new one.",
                    HttpStatus.BAD_REQUEST, "LINK_INVALID");
        }
        return t;
    }

    /* ── Permissions ── */

    /** Every permission name the user holds; super admins hold all of them. */
    public List<String> effectivePermissions(User user) {
        if (user.isSuperAdmin()) {
            return permissions.findAllByOrderByNameAsc().stream().map(Permission::getName).toList();
        }
        TreeSet<String> names = new TreeSet<>();
        user.getRoles().forEach(r -> r.getPermissions().forEach(p -> names.add(p.getName())));
        return List.copyOf(names);
    }
}
