package com.atelier.api.service;

import com.atelier.api.config.AuditDataChangeListener;
import com.atelier.api.config.AuditRequestInterceptor;
import com.atelier.api.entity.AuditLog;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.repository.AuditLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit log's front door. Records arrive from four places:
 *
 *   REQUEST   AuditRequestInterceptor  - every API call that changes something (automatic)
 *   DATA      AuditDataChangeListener  - every row inserted / updated / deleted (automatic)
 *   BUSINESS  logAction(...)           - a service saying, in words, what happened
 *   AUTH      recordAuth(...)          - sign-in / failed sign-in / sign-out
 */
@Service
public class AuditLogService {

    public static final int MAX_RESULTS = 5000;
    public static final int DEFAULT_RESULTS = 2000;

    private final AuditLogRepository repository;
    private final AuditWriter writer;

    public AuditLogService(AuditLogRepository repository, AuditWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    /**
     * Records a business event inside the caller's transaction, so it rolls back with it.
     * Use it from your own services: auditLogService.logAction("Invoice", id, AuditAction.UPDATE, null, "Approved invoice INV-12").
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void logAction(String entityName, Long entityId, AuditAction action, String performedBy, String details) {
        AuditLog log = new AuditLog();
        log.setCategory(AuditLog.CATEGORY_BUSINESS);
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setPerformedBy(performedBy == null || performedBy.isBlank() ? AuditContext.currentActor() : performedBy);
        log.setPerformedAt(LocalDateTime.now());
        log.setDetails(details);
        AuditContext.stampRequest(log);
        repository.save(log);
    }

    /** Records a sign-in event in the background. Never throws. */
    public void recordAuth(AuditAction action, String email, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setCategory(AuditLog.CATEGORY_AUTH);
            log.setEntityName("Auth");
            log.setAction(action);
            log.setPerformedBy(email == null || email.isBlank() ? AuditContext.SYSTEM_ACTOR : AuditContext.truncate(email, 255));
            log.setPerformedAt(LocalDateTime.now());
            log.setDetails(details);
            AuditContext.stampRequest(log);
            writer.writeAsync(log);
        } catch (Exception ignored) {
            // never let auditing break authentication
        }
    }

    /** Newest-first search. Every filter is optional. */
    @Transactional(readOnly = true)
    public List<AuditLog> search(LocalDate from, LocalDate to, String category, String search, Integer limit) {
        // Never bind NULL: PostgreSQL can't infer its type. Use open bounds / empty strings instead.
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.of(2999, 1, 1, 0, 0);
        String cat = category == null ? "" : category.trim().toUpperCase();
        String term = search == null ? "" : search.trim().toLowerCase();
        int size = limit == null ? DEFAULT_RESULTS : Math.max(1, Math.min(limit, MAX_RESULTS));
        return repository.search(start, end, cat, term, "%" + term + "%", PageRequest.of(0, size));
    }
}
