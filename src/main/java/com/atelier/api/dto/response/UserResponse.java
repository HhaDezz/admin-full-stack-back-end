package com.atelier.api.dto.response;

import com.atelier.api.entity.User;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Comparator;

public record UserResponse(Long id, String name, String email, String phone, String address, boolean enabled,
                           boolean system, List<RoleResponse> roles, LocalDateTime lastLoginAt,
                           LocalDateTime createdAt, LocalDateTime updatedAt, boolean emailVerified,
                           boolean twoFactorEnabled) {

    public static UserResponse of(User u) {
        List<RoleResponse> roles = u.getRoles().stream()
                .sorted(Comparator.comparing(r -> r.getName()))
                .map(r -> RoleResponse.of(r, null)).toList();
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getAddress(), u.isEnabled(),
                u.isSystem(), roles, u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt(), u.isEmailVerified(),
                u.isTwoFactorEnabled());
    }
}
