package com.atelier.api.dto.request.create;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserRequest(
        @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 255) String email,
        @Size(max = 40, message = "Phone is too long") String phone,
        @Size(max = 255, message = "Address is too long") String address,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
        List<Long> roleIds,
        Boolean enabled) {
}
