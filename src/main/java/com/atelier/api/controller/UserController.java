package com.atelier.api.controller;

import com.atelier.api.dto.request.create.UserRequest;
import com.atelier.api.dto.request.update.UserStatusRequest;
import com.atelier.api.dto.request.update.UserUpdateRequest;
import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.UserResponse;
import com.atelier.api.entity.User;
import com.atelier.api.service.UserService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('admin-user-view')")
    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(service.list().stream().map(UserResponse::of).toList());
    }

    @PreAuthorize("hasAuthority('admin-user-view')")
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(UserResponse.of(service.get(id)));
    }

    @PreAuthorize("hasAuthority('admin-user-create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserRequest body) {
        return ApiResponse.ok("User created", UserResponse.of(service.create(body)));
    }

    @PreAuthorize("hasAuthority('admin-user-edit')")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest body) {
        return ApiResponse.ok("User updated", UserResponse.of(service.update(id, body)));
    }

    @PreAuthorize("hasAuthority('admin-user-edit')")
    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> status(@PathVariable Long id, @Valid @RequestBody UserStatusRequest body) {
        User user = service.setEnabled(id, body.enabled());
        return ApiResponse.ok(body.enabled() ? "User enabled" : "User disabled", UserResponse.of(user));
    }

    @PreAuthorize("hasAuthority('admin-user-delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("User deleted", null);
    }

    /** For a user who lost their phone: turns two-step sign-in off so they can sign in with their password. */
    @PreAuthorize("hasAuthority('admin-user-edit')")
    @PostMapping("/{id}/2fa/reset")
    public ApiResponse<UserResponse> resetTwoFactor(@PathVariable Long id) {
        return ApiResponse.ok("Two-step sign-in turned off", UserResponse.of(service.resetTwoFactor(id)));
    }
}
