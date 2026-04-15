package com.atelier.api.controller;

import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.PermissionResponse;
import com.atelier.api.repository.PermissionRepository;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionRepository permissions;

    public PermissionController(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    @PreAuthorize("hasAuthority('admin-role-view')")
    @GetMapping
    public ApiResponse<List<PermissionResponse>> list() {
        return ApiResponse.ok(permissions.findAllByOrderByNameAsc().stream()
                .map(PermissionResponse::from).toList());
    }
}
