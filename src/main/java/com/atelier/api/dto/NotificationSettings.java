package com.atelier.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Which notifications a user gets, per category and channel. Stored as JSON on the user.
 *   security - new sign-ins, password and two-step changes
 *   account  - changes an administrator makes to your account (roles, status)
 *   product  - announcements and tips
 */
public record NotificationSettings(@NotNull @Valid Channels security, @NotNull @Valid Channels account,
                                   @NotNull @Valid Channels product) {

    public record Channels(boolean inApp, boolean email) {
    }

    public static NotificationSettings defaults() {
        return new NotificationSettings(new Channels(true, true), new Channels(true, false), new Channels(true, false));
    }

    public Channels of(String category) {
        return switch (category) {
            case "SECURITY" -> security;
            case "ACCOUNT" -> account;
            default -> product;
        };
    }
}
