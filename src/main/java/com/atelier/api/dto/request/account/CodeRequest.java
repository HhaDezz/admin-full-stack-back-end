package com.atelier.api.dto.request.account;

import jakarta.validation.constraints.NotBlank;

public record CodeRequest(@NotBlank(message = "Enter the code") String code) {
}
