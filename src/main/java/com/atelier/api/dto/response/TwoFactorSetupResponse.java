package com.atelier.api.dto.response;

/** Shown once during setup: the secret (for manual entry) and the otpauth link (for the QR code). */
public record TwoFactorSetupResponse(String secret, String otpauthUrl) {
}
