package com.atelier.api.dto.request.update;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank(message = "Enter a new password") @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters") String newPassword) {
}
