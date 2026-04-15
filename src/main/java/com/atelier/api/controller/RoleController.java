package com.atelier.api.controller;

import com.atelier.api.dto.request.create.RoleRequest;
import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.RoleResponse;
import com.atelier.api.entity.Role;
import com.atelier.api.service.RoleService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


import jakarta.validation.Valid;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('admin-role-view')")
    @GetMapping
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PreAuthorize("hasAuthority('admin-role-view')")
    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PreAuthorize("hasAuthority('admin-role-create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleRequest body) {
        return ApiResponse.ok("Role created", service.create(body));
    }

    @PreAuthorize("hasAuthority('admin-role-edit')")
    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleRequest body) {
        return ApiResponse.ok("Role updated", service.update(id, body));
    }

    /** Body: a JSON array of permission ids. Replaces the role's permissions. */
    @PreAuthorize("hasAuthority('admin-role-edit')")
    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleResponse> assign(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        return ApiResponse.ok("Permissions saved", service.assignPermissions(id, permissionIds));
    }

    @PreAuthorize("hasAuthority('admin-role-delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Role deleted", null);
    }
}
