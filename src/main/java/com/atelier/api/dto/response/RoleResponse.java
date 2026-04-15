package com.atelier.api.dto.response;

import com.atelier.api.entity.Permission;
import com.atelier.api.entity.Role;

import java.util.List;

public record RoleResponse(Long id, String roleName, String description, boolean system,
                           List<String> permissions, Long userCount) {

    public static RoleResponse of(Role role, Long userCount) {
        List<String> perms = role.getPermissions().stream().map(Permission::getName).sorted().toList();
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystem(), perms, userCount);
    }
}
