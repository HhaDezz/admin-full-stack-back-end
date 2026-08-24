package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 255) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {
}
