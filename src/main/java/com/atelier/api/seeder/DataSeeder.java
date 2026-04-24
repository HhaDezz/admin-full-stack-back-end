package com.atelier.api.seeder;

import com.atelier.api.config.AppProperties;
import com.atelier.api.entity.Role;
import com.atelier.api.entity.User;
import com.atelier.api.repository.RoleRepository;
import com.atelier.api.repository.UserRepository;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * First start only: creates the built-in super admin from app.bootstrap.* (ADMIN_EMAIL /
 * ADMIN_PASSWORD), and optionally a few demo users. Never touches existing accounts,
 * so changing those settings later does not reset anyone's password.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEFAULT_PASSWORD = "ChangeMe123!";

    private final AppProperties props;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public DataSeeder(AppProperties props, UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap cfg = props.bootstrap();
        if (!users.existsSuperAdmin()) {
            Role superAdmin = roles.findByName(Role.SUPER_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing: migrations did not run"));
            String email = cfg.adminEmail().trim().toLowerCase();
            User admin = users.findByEmail(email).orElseGet(User::new);
            admin.setName(cfg.adminName());
            admin.setEmail(email);
            admin.setPasswordHash(encoder.encode(cfg.adminPassword()));
            admin.setEnabled(true);
            admin.setSystem(true);
            if (admin.getEmailVerifiedAt() == null) admin.setEmailVerifiedAt(java.time.LocalDateTime.now());
            admin.getRoles().add(superAdmin);
            users.save(admin);
            log.info("Created super admin {}", email);
            if (DEFAULT_PASSWORD.equals(cfg.adminPassword())) {
                log.warn("The super admin is using the default password. Sign in and change it, "
                        + "or set ADMIN_PASSWORD before the first start.");
            }
        }
        if (cfg.demoUsers()) seedDemoUsers();
    }

    private void seedDemoUsers() {
        Role admin = roles.findByName("ADMIN").orElse(null);
        Role member = roles.findByName("USER").orElse(null);
        List<Object[]> people = List.of(
                new Object[] { "Alex Morgan", "alex@example.com", admin },
                new Object[] { "Priya Shah", "priya@example.com", member },
                new Object[] { "Daniel Okafor", "daniel@example.com", member },
                new Object[] { "Mei Chen", "mei@example.com", member });
        for (Object[] p : people) {
            String email = (String) p[1];
            if (users.existsByEmail(email)) continue;
            User u = new User();
            u.setName((String) p[0]);
            u.setEmail(email);
            u.setPasswordHash(encoder.encode("Password123!"));
            u.setEmailVerifiedAt(java.time.LocalDateTime.now());
            if (p[2] != null) u.setRoles(new java.util.HashSet<>(Set.of((Role) p[2])));
            users.save(u);
        }
        log.info("Demo users ready (password: Password123!)");
    }
}
