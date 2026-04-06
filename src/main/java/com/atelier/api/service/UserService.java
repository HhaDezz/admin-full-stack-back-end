package com.atelier.api.service;

import com.atelier.api.dto.request.create.UserRequest;
import com.atelier.api.dto.request.update.PasswordChangeRequest;
import com.atelier.api.dto.request.update.ProfileUpdateRequest;
import com.atelier.api.dto.request.update.UserUpdateRequest;
import com.atelier.api.entity.Role;
import com.atelier.api.entity.User;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.exception.BusinessException;
import com.atelier.api.repository.RecoveryCodeRepository;
import com.atelier.api.repository.RoleRepository;
import com.atelier.api.repository.UserRepository;
import com.atelier.api.security.SessionService;
import com.atelier.api.security.AuthUser;
import com.atelier.api.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditLogService audit;
    private final SessionService sessions;
    private final NotificationService notifications;
    private final RecoveryCodeRepository recoveryCodes;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditLogService audit,
                       SessionService sessions, NotificationService notifications, RecoveryCodeRepository recoveryCodes) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
        this.sessions = sessions;
        this.notifications = notifications;
        this.recoveryCodes = recoveryCodes;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("User"));
    }

    @Transactional
    public User create(UserRequest req) {
        String email = normaliseEmail(req.email());
        if (users.existsByEmail(email)) throw BusinessException.conflict("That email is already in use.", "email");
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(req.phone()));
        user.setAddress(blankToNull(req.address()));
        user.setPasswordHash(encoder.encode(req.password()));
        user.setEnabled(req.enabled() == null || req.enabled());
        user.setRoles(new HashSet<>(resolveRoles(req.roleIds())));
        // An administrator vouches for the address, so no verification email is needed.
        user.setEmailVerifiedAt(LocalDateTime.now());
        return users.save(user);
    }

    @Transactional
    public User update(Long id, UserUpdateRequest req) {
        User user = get(id);
        assertManageable(user, "edit");
        String email = normaliseEmail(req.email());
        if (users.existsByEmailAndIdNot(email, id)) throw BusinessException.conflict("That email is already in use.", "email");
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(req.phone()));
        user.setAddress(blankToNull(req.address()));
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(encoder.encode(req.password()));
            user.setCredentialsChangedAt(LocalDateTime.now());
            sessions.revokeAll(user.getId(), null);
            notifications.notify(user, NotificationService.SECURITY, "PASSWORD_CHANGED", "An administrator set a new password",
                    "Your password was changed by an administrator and you were signed out everywhere.", "/settings/security");
        }
        if (req.roleIds() != null) {
            Set<String> before = roleNames(user);
            user.setRoles(new HashSet<>(resolveRoles(req.roleIds())));
            Set<String> after = roleNames(user);
            if (!before.equals(after)) {
                notifications.notify(user, NotificationService.ACCOUNT, "ROLES_CHANGED", "Your roles changed",
                        after.isEmpty() ? "You no longer have any roles." : "You now have: " + String.join(", ", after) + ".", "/");
            }
        }
        if (req.enabled() != null && req.enabled() != user.isEnabled()) {
            if (!req.enabled()) assertNotSelf(user, "disable");
            user.setEnabled(req.enabled());
            if (!req.enabled()) sessions.revokeAll(user.getId(), null);
        }
        return users.save(user);
    }

    /** For users who lost their phone: turns two-step sign-in off so they can sign in with a password. */
    @Transactional
    public User resetTwoFactor(Long id) {
        User user = get(id);
        assertManageable(user, "change");
        if (!user.isTwoFactorEnabled()) throw BusinessException.badRequest("Two-step sign-in is already off for this user.", null);
        user.setTotpSecret(null);
        user.setTotpEnabledAt(null);
        user.setTotpLastStep(null);
        recoveryCodes.deleteForUser(user.getId());
        audit.logAction("User", user.getId(), AuditAction.TWO_FACTOR_DISABLED, null, "Reset two-step sign-in for " + user.getEmail());
        notifications.notify(user, NotificationService.SECURITY, "TWO_FACTOR_DISABLED", "Two-step sign-in was turned off",
                "An administrator turned off two-step sign-in for your account. Turn it back on in Settings → Security.", "/settings/security");
        return users.save(user);
    }

    @Transactional
    public User setEnabled(Long id, boolean enabled) {
        User user = get(id);
        assertManageable(user, enabled ? "enable" : "disable");
        if (!enabled) assertNotSelf(user, "disable");
        user.setEnabled(enabled);
        if (!enabled) sessions.revokeAll(user.getId(), null);
        return users.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = get(id);
        assertManageable(user, "delete");
        assertNotSelf(user, "delete");
        users.delete(user);
    }

    /* ── The signed-in user's own account ── */

    @Transactional(readOnly = true)
    public User me() {
        return get(CurrentUser.get().id());
    }

    @Transactional
    public User updateProfile(ProfileUpdateRequest req) {
        User user = me();
        user.setName(req.name().trim());
        user.setPhone(blankToNull(req.phone()));
        user.setAddress(blankToNull(req.address()));
        return users.save(user);
    }

    @Transactional
    public User changePassword(PasswordChangeRequest req) {
        User user = me();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("Your current password is incorrect.", "currentPassword");
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("Choose a password you haven't used here before.", "newPassword");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        // Signs out every other session that holds an older token.
        user.setCredentialsChangedAt(LocalDateTime.now());
        audit.logAction("User", user.getId(), AuditAction.PASSWORD_CHANGED, user.getEmail(), "Changed own password");
        notifications.notify(user, NotificationService.SECURITY, "PASSWORD_CHANGED", "Your password was changed",
                "Your password was changed and your other devices were signed out.", "/settings/security");
        return users.save(user);
    }

    /* ── Rules ── */

    private List<Role> resolveRoles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Role> found = roles.findAllById(ids);
        if (found.size() != new HashSet<>(ids).size()) throw BusinessException.badRequest("One or more roles don't exist.", "roleIds");
        if (found.stream().anyMatch(Role::isSystem) && !CurrentUser.isSuperAdmin()) {
            throw BusinessException.forbidden("Only a super admin can assign the " + Role.SUPER_ADMIN + " role.");
        }
        return found;
    }

    /** The bootstrap account and other super admins are protected from non-super-admins. */
    private void assertManageable(User target, String verb) {
        if (target.isSystem()) {
            throw BusinessException.forbidden("The built-in administrator account can't be changed here. "
                    + "Its owner can update it from their profile.");
        }
        if (target.isSuperAdmin() && !CurrentUser.isSuperAdmin()) {
            throw BusinessException.forbidden("Only a super admin can " + verb + " another super admin.");
        }
    }

    private void assertNotSelf(User target, String verb) {
        AuthUser me = CurrentUser.get();
        if (Objects.equals(me.id(), target.getId())) {
            throw new BusinessException("You can't " + verb + " your own account.", HttpStatus.BAD_REQUEST);
        }
    }

    private static Set<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
