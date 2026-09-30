package com.atelier.api.dto.response;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, long total, long unreadCount, int page, int size) {
}
