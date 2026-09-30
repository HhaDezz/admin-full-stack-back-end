package com.atelier.api.dto.response;

import com.atelier.api.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String category, String type, String title, String body, String link,
                                   boolean read, LocalDateTime createdAt) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getCategory(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
