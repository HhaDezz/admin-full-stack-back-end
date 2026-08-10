package com.atelier.api.dto.request.update;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
        @Size(max = 40, message = "Phone is too long") String phone,
        @Size(max = 255, message = "Address is too long") String address) {
}
