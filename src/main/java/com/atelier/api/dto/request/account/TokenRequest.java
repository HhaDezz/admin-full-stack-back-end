package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "The link is incomplete") String token) {
}
