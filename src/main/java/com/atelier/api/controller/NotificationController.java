package com.atelier.api.controller;

import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.NotificationPageResponse;
import com.atelier.api.dto.response.NotificationResponse;
import com.atelier.api.entity.Notification;
import com.atelier.api.security.CurrentUser;
import com.atelier.api.service.NotificationService;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's notifications (the bell in the top bar and the notification centre). */
@RestController
@RequestMapping("/me/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<NotificationPageResponse> list(@RequestParam(defaultValue = "false") boolean unread,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long me = CurrentUser.get().id();
        Page<Notification> p = notifications.list(me, unread, page, size);
        return ApiResponse.ok(new NotificationPageResponse(p.map(NotificationResponse::of).getContent(), p.getTotalElements(),
                notifications.unreadCount(me), p.getNumber(), p.getSize()));
    }

    /** Just the unread count, for the bell badge (cheap to poll). */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notifications.unreadCount(CurrentUser.get().id())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> read(@PathVariable Long id) {
        return ApiResponse.ok(NotificationResponse.of(notifications.markRead(CurrentUser.get().id(), id, true)));
    }

    @PostMapping("/{id}/unread")
    public ApiResponse<NotificationResponse> unread(@PathVariable Long id) {
        return ApiResponse.ok(NotificationResponse.of(notifications.markRead(CurrentUser.get().id(), id, false)));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll() {
        int n = notifications.markAllRead(CurrentUser.get().id());
        return ApiResponse.ok(n == 0 ? "Nothing unread" : "Marked " + n + " as read", Map.of("updated", n));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notifications.delete(CurrentUser.get().id(), id);
        return ApiResponse.ok("Notification deleted", null);
    }
}
