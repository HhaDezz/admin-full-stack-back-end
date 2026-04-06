package com.atelier.api.dto.request.update;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {
}
