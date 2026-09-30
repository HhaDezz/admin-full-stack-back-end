package com.atelier.api.service;

import com.atelier.api.dto.NotificationSettings;
import com.atelier.api.entity.Notification;
import com.atelier.api.entity.User;
import com.atelier.api.exception.BusinessException;
import com.atelier.api.repository.NotificationRepository;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a notification in-app and/or by email, according to the user's preferences.
 * Call it from your own services: notifications.notify(user, "ACCOUNT", "INVOICE_APPROVED", "Invoice approved", "INV-12 was approved.", "/invoices/12");
 */
@Service
public class NotificationService {

    public static final String SECURITY = "SECURITY";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String PRODUCT = "PRODUCT";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NotificationRepository repository;
    private final MailService mail;

    public NotificationService(NotificationRepository repository, MailService mail) {
        this.repository = repository;
        this.mail = mail;
    }

    @Transactional
    public void notify(User user, String category, String type, String title, String body, String link) {
        NotificationSettings.Channels ch = settingsOf(user).of(category);
        if (ch.inApp()) {
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setCategory(category);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setLink(link);
            repository.save(n);
        }
        if (ch.email()) {
            if (SECURITY.equals(category)) mail.securityAlert(user.getEmail(), user.getName(), title, body, link == null ? "/settings/security" : link);
            else mail.notification(user.getEmail(), user.getName(), title, body, link);
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        return unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, p)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, p);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(Long userId, Long id, boolean read) {
        Notification n = repository.findByIdAndUserId(id, userId).orElseThrow(() -> BusinessException.notFound("Notification"));
        n.setReadAt(read ? (n.getReadAt() == null ? LocalDateTime.now() : n.getReadAt()) : null);
        return n;
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, LocalDateTime.now());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(repository.findByIdAndUserId(id, userId).orElseThrow(() -> BusinessException.notFound("Notification")));
    }

    public NotificationSettings settingsOf(User user) {
        if (user.getNotificationSettings() == null) return NotificationSettings.defaults();
        try {
            NotificationSettings s = JSON.readValue(user.getNotificationSettings(), NotificationSettings.class);
            return s.security() == null || s.account() == null || s.product() == null ? NotificationSettings.defaults() : s;
        } catch (RuntimeException e) {
            return NotificationSettings.defaults();
        }
    }

    public String toJson(NotificationSettings settings) {
        return JSON.writeValueAsString(settings);
    }

    /** Keeps the table small: notifications older than 180 days are removed nightly. */
    @Scheduled(cron = "0 27 3 * * *")
    @Transactional
    public void purge() {
        int n = repository.deleteOlderThan(LocalDateTime.now().minusDays(180));
        if (n > 0) log.info("Removed {} old notifications", n);
    }
}
