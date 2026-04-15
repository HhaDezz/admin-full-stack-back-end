package com.atelier.api.dto.request.create;

import com.atelier.api.entity.Permission;
import com.atelier.api.entity.Role;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 64, message = "Role name is too long")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Use capital letters, digits and underscores, e.g. SALES_MANAGER")
        String roleName,
        @Size(max = 255, message = "Description is too long") String description,
        /* Permission names. Leave out (null) on update to keep the current ones. */
        List<String> permissions) {
}
