package com.atelier.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "app." in application.properties. Each can be overridden with an
 * environment variable (see application.properties.example). Missing values fall back
 * to the defaults below, except app.jwt.secret, which must be set.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Bootstrap bootstrap, Security security, Registration registration,
                            Mail mail, String webUrl, String brandName) {

    public AppProperties {
        jwt = jwt == null ? new Jwt(null, null) : jwt;
        cors = cors == null ? new Cors(null) : cors;
        bootstrap = bootstrap == null ? new Bootstrap(null, null, null, false) : bootstrap;
        security = security == null ? new Security(0, null) : security;
        registration = registration == null ? new Registration(false, true, null) : registration;
        mail = mail == null ? new Mail(null) : mail;
        webUrl = webUrl == null || webUrl.isBlank() ? "http://localhost:5173" : webUrl.replaceAll("/+$", "");
        brandName = brandName == null || brandName.isBlank() ? "Anjana" : brandName;
    }

    public record Jwt(String secret, Duration expiration) {
        public Jwt {
            expiration = expiration == null ? Duration.ofHours(8) : expiration;
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("http://localhost:5173") : allowedOrigins;
        }
    }

    /** The first super admin, created on first start when no super admin exists. */
    public record Bootstrap(String adminName, String adminEmail, String adminPassword, boolean demoUsers) {
        public Bootstrap {
            adminName = adminName == null || adminName.isBlank() ? "Administrator" : adminName;
            adminEmail = adminEmail == null || adminEmail.isBlank() ? "admin@example.com" : adminEmail;
            adminPassword = adminPassword == null || adminPassword.isBlank() ? "ChangeMe123!" : adminPassword;
        }
    }

    /** Sign-in throttling: this many failures within the window locks the email + IP for the window. */
    public record Security(int maxLoginAttempts, Duration loginLockout) {
        public Security {
            maxLoginAttempts = maxLoginAttempts <= 0 ? 5 : maxLoginAttempts;
            loginLockout = loginLockout == null ? Duration.ofMinutes(15) : loginLockout;
        }
    }

    /**
     * Self sign-up. Off unless enabled. New accounts get defaultRole and, when
     * requireVerification is on, can't sign in until they click the link in the email.
     */
    public record Registration(boolean enabled, Boolean requireVerification, String defaultRole) {
        public Registration {
            requireVerification = requireVerification == null || requireVerification;
            defaultRole = defaultRole == null || defaultRole.isBlank() ? "USER" : defaultRole;
        }
    }

    /** Sender address for account emails. SMTP itself is configured with spring.mail.*. */
    public record Mail(String from) {
        public Mail {
            from = from == null || from.isBlank() ? "no-reply@example.com" : from;
        }
    }
}
