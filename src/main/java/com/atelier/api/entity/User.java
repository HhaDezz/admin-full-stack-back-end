package com.atelier.api.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** Always stored lower-case, so sign-in is case-insensitive. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Disabled users can't sign in, and their existing sessions stop working immediately. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** The bootstrap super admin. Can't be edited or deleted by others. */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Tokens issued before this moment are rejected (set when the password changes). */
    @Column(name = "credentials_changed_at")
    private LocalDateTime credentialsChangedAt;

    /** Null until the owner clicks the link in the verification email. */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** Authenticator-app secret, encrypted. Set during setup; active once totpEnabledAt is set. */
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    @Column(name = "totp_enabled_at")
    private LocalDateTime totpEnabledAt;

    /** Last accepted 30-second step, so the same code can't be used twice. */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /** Notification preferences as JSON (see NotificationSettings). Null means the defaults. */
    @Column(name = "notification_settings", columnDefinition = "TEXT")
    private String notificationSettings;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isTwoFactorEnabled() {
        return totpEnabledAt != null && totpSecret != null;
    }

    public boolean isSuperAdmin() {
        return roles.stream().anyMatch(Role::isSystem);
    }
}
