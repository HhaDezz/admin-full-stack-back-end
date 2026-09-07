package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.NotBlank;

/** The second sign-in step: a six-digit code from the app, or one recovery code. */
public record TwoFactorLoginRequest(@NotBlank(message = "Sign in again") String challengeToken,
                                    @NotBlank(message = "Enter the code") String code) {
}
