package com.atelier.api.dto.response;

import java.time.LocalDateTime;

/** What the Security tab shows at a glance. */
public record SecurityOverviewResponse(boolean emailVerified, boolean twoFactorEnabled, long recoveryCodesLeft,
                                       LocalDateTime passwordChangedAt, int activeSessions) {
}
