package com.atelier.api.service;

import com.atelier.api.entity.AuditLog;
import com.atelier.api.repository.AuditLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Saves audit records on a background thread.
 *
 * Two rules this class exists to enforce:
 *
 *  1. Auditing must never slow a user down. @Async returns to the caller
 *     immediately; the INSERT happens on another thread.
 *
 *  2. Auditing must never break the thing it is recording. Every failure is
 *     caught and logged to the application log — a lost audit row is bad, a
 *     checkout that fails because the audit table had a problem is worse.
 *
 * It is a separate bean on purpose: @Async only works when the method is
 * called from ANOTHER bean (Spring wraps the bean in a proxy; a call from
 * inside the same class skips the proxy and runs synchronously).
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditLogRepository auditLogRepository;

    public AuditWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void writeAsync(AuditLog entry) {
        try {
            // save() opens its own short transaction, independent of the
            // request that produced the entry (which has already finished).
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit entry could not be saved ({} {} {}): {}",
                    entry.getCategory(), entry.getAction(), entry.getEntityName(), e.getMessage());
        }
    }
}
