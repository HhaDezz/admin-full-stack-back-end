package com.atelier.api.dto.response;

import com.atelier.api.entity.Permission;

public record PermissionResponse(Long id, String permissionName, String description) {

    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getName(), p.getDescription());
    }
}
