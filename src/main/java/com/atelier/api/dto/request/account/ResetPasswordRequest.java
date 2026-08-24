package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "The link is incomplete") String token,
        @NotBlank(message = "Enter a new password") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {
}
