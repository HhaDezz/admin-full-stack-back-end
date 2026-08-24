package com.atelier.api.dto.response;

/** Public settings the sign-in pages need (e.g. whether to show "Create account"). */
public record AuthOptionsResponse(boolean registrationEnabled, boolean requireEmailVerification, String brandName) {
}
