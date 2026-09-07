package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.NotBlank;

/** Re-entering the password before a sensitive change, or to unlock a locked screen. */
public record PasswordConfirmRequest(@NotBlank(message = "Enter your password") String password) {
}
