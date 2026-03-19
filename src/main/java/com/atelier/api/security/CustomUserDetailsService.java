package com.atelier.api.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.atelier.api.entity.Permission;
import com.atelier.api.entity.Role;
import com.atelier.api.entity.User;
import com.atelier.api.repository.PermissionRepository;
import com.atelier.api.repository.UserRepository;

/**
 * Turns a user's roles into Spring Security authorities:
 *   ROLE_{roleName}     e.g. ROLE_ADMIN
 *   {permissionName}    e.g. admin-user-edit
 * Holders of a system role (SUPER_ADMIN) also get SYSTEM_USER and every permission
 * that exists right now, so new permissions are covered the moment they are migrated in.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public CustomUserDetailsService(UserRepository userRepository, PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    public Set<GrantedAuthority> authoritiesFor(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            for (Permission p : role.getPermissions()) authorities.add(new SimpleGrantedAuthority(p.getName()));
        }
        if (user.isSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority(CurrentUser.SYSTEM_AUTHORITY));
            for (Permission p : permissionRepository.findAll()) authorities.add(new SimpleGrantedAuthority(p.getName()));
        }
        return authorities;
    }

    /** Standard Spring Security lookup by email (also stops Boot creating a default in-memory user). */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(authoritiesFor(user))
                .build();
    }
}
