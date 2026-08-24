package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRequest(@NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email) {
}
