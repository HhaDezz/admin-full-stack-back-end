package com.atelier.api.service;

import com.atelier.api.dto.NotificationSettings;
import com.atelier.api.dto.response.ActivityResponse;
import com.atelier.api.dto.response.RecoveryCodesResponse;
import com.atelier.api.dto.response.SecurityOverviewResponse;
import com.atelier.api.dto.response.SessionResponse;
import com.atelier.api.dto.response.TwoFactorSetupResponse;
import com.atelier.api.entity.RecoveryCode;
import com.atelier.api.entity.User;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.exception.BusinessException;
import com.atelier.api.repository.AuditLogRepository;
import com.atelier.api.repository.RecoveryCodeRepository;
import com.atelier.api.repository.UserRepository;
import com.atelier.api.security.AuthUser;
import com.atelier.api.security.CurrentUser;
import com.atelier.api.security.Hashing;
import com.atelier.api.security.LoginAttemptService;
import com.atelier.api.security.SecretCipher;
import com.atelier.api.security.SessionService;
import com.atelier.api.security.TotpService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's own security settings: two-step sign-in, devices, notifications, activity. */
@Service
public class AccountService {

    private static final int RECOVERY_CODES = 10;

    private final UserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final SessionService sessions;
    private final LoginAttemptService attempts;
    private final AuditLogService audit;
    private final NotificationService notifications;

    public AccountService(UserRepository users, RecoveryCodeRepository recoveryCodes, AuditLogRepository auditLogs,
                          PasswordEncoder encoder, TotpService totp, SecretCipher cipher, SessionService sessions,
                          LoginAttemptService attempts, AuditLogService audit, NotificationService notifications) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.auditLogs = auditLogs;
        this.encoder = encoder;
        this.totp = totp;
        this.cipher = cipher;
        this.sessions = sessions;
        this.attempts = attempts;
        this.audit = audit;
        this.notifications = notifications;
    }

    private User me() {
        return users.findById(CurrentUser.get().id()).orElseThrow(() -> BusinessException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public SecurityOverviewResponse overview() {
        User u = me();
        return new SecurityOverviewResponse(u.isEmailVerified(), u.isTwoFactorEnabled(),
                u.isTwoFactorEnabled() ? recoveryCodes.countByUserIdAndUsedAtIsNull(u.getId()) : 0,
                u.getCredentialsChangedAt(), sessions.active(u.getId()).size());
    }

    /** Re-checks the password (lock screen, and before sensitive changes). Throttled like sign-in. */
    @Transactional(readOnly = true)
    public void verifyPassword(String password, String ip) {
        checkPassword(me(), password, ip);
    }

    private void checkPassword(User user, String password, String ip) {
        String key = "confirm:" + user.getEmail();
        long wait = attempts.lockedForSeconds(key, ip);
        if (wait > 0) {
            throw new BusinessException("Too many wrong passwords. Try again in " + Math.max(1, (wait + 59) / 60) + " minute(s).", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            attempts.recordFailure(key, ip);
            throw BusinessException.badRequest("That password isn't right.", "password");
        }
        attempts.recordSuccess(key, ip);
    }

    /* ── Two-step sign-in ── */

    /** Step 1: a new secret, stored but not active until a code from the app confirms it. */
    @Transactional
    public TwoFactorSetupResponse startTwoFactor() {
        User u = me();
        if (u.isTwoFactorEnabled()) throw BusinessException.badRequest("Two-step sign-in is already on.", null);
        String secret = totp.newSecret();
        u.setTotpSecret(cipher.encrypt(secret));
        u.setTotpEnabledAt(null);
        return new TwoFactorSetupResponse(secret, totp.otpauthUrl(u.getEmail(), secret));
    }

    /** Step 2: the first code proves the app is set up. Returns the recovery codes (shown once). */
    @Transactional
    public RecoveryCodesResponse enableTwoFactor(String code) {
        User u = me();
        if (u.isTwoFactorEnabled()) throw BusinessException.badRequest("Two-step sign-in is already on.", null);
        String secret = cipher.decrypt(u.getTotpSecret());
        if (secret == null) throw BusinessException.badRequest("Start the setup again.", null);
        long step = totp.verify(secret, code, null);
        if (step < 0) throw BusinessException.badRequest("That code didn't work. Check the time on your phone and try again.", "code");
        u.setTotpEnabledAt(LocalDateTime.now());
        u.setTotpLastStep(step);
        audit.logAction("User", u.getId(), AuditAction.TWO_FACTOR_ENABLED, u.getEmail(), "Turned on two-step sign-in");
        notifications.notify(u, NotificationService.SECURITY, "TWO_FACTOR_ENABLED", "Two-step sign-in is on",
                "From now on you'll enter a code from your authenticator app when you sign in.", "/settings/security");
        return newRecoveryCodes(u);
    }

    @Transactional
    public void disableTwoFactor(String password, String ip) {
        User u = me();
        checkPassword(u, password, ip);
        if (!u.isTwoFactorEnabled()) throw BusinessException.badRequest("Two-step sign-in is already off.", null);
        u.setTotpSecret(null);
        u.setTotpEnabledAt(null);
        u.setTotpLastStep(null);
        recoveryCodes.deleteForUser(u.getId());
        audit.logAction("User", u.getId(), AuditAction.TWO_FACTOR_DISABLED, u.getEmail(), "Turned off two-step sign-in");
        notifications.notify(u, NotificationService.SECURITY, "TWO_FACTOR_DISABLED", "Two-step sign-in is off",
                "Your account is now protected by your password only.", "/settings/security");
    }

    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(String password, String ip) {
        User u = me();
        checkPassword(u, password, ip);
        if (!u.isTwoFactorEnabled()) throw BusinessException.badRequest("Turn on two-step sign-in first.", null);
        audit.logAction("User", u.getId(), AuditAction.UPDATE, u.getEmail(), "Created new recovery codes");
        return newRecoveryCodes(u);
    }

    private RecoveryCodesResponse newRecoveryCodes(User u) {
        recoveryCodes.deleteForUser(u.getId());
        List<String> codes = new ArrayList<>(RECOVERY_CODES);
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = Hashing.recoveryCode();
            codes.add(code);
            recoveryCodes.save(new RecoveryCode(u.getId(), Hashing.sha256(Hashing.normaliseCode(code))));
        }
        return new RecoveryCodesResponse(codes);
    }

    /* ── Devices ── */

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions() {
        AuthUser me = CurrentUser.get();
        return sessions.active(me.id()).stream().map(s -> SessionResponse.of(s, me.sessionId())).toList();
    }

    @Transactional
    public void revokeSession(String id) {
        AuthUser me = CurrentUser.get();
        if (id.equals(me.sessionId())) {
            throw BusinessException.badRequest("That's this device. Use Sign out instead.", null);
        }
        if (!sessions.revoke(me.id(), id)) throw BusinessException.notFound("Session");
        audit.recordAuth(AuditAction.SESSION_REVOKED, me.email(), "Signed out another device");
    }

    @Transactional
    public int revokeOtherSessions() {
        AuthUser me = CurrentUser.get();
        int n = sessions.revokeAll(me.id(), me.sessionId());
        if (n > 0) audit.recordAuth(AuditAction.SESSION_REVOKED, me.email(), "Signed out " + n + " other device(s)");
        return n;
    }

    /* ── Notifications and activity ── */

    @Transactional(readOnly = true)
    public NotificationSettings notificationSettings() {
        return notifications.settingsOf(me());
    }

    @Transactional
    public NotificationSettings updateNotificationSettings(NotificationSettings settings) {
        User u = me();
        u.setNotificationSettings(notifications.toJson(settings));
        return settings;
    }

    /** The last 90 days of the user's own sign-ins and changes. */
    @Transactional(readOnly = true)
    public List<ActivityResponse> activity(int limit) {
        AuthUser me = CurrentUser.get();
        return auditLogs.activityOf(me.email().toLowerCase(), LocalDateTime.now().minusDays(90),
                        PageRequest.of(0, Math.max(1, Math.min(limit, 500))))
                .stream().map(ActivityResponse::of).toList();
    }
}
