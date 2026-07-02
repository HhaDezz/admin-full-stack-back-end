package com.atelier.api.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Housekeeping for the audit schema: make sure the coming months have a
 * partition to write into, and drop the months that have aged out.
 *
 * The real work is two database functions created in V4__audit_schema.sql — this class only
 * decides when to call them. Retention is set by audit.retention-months
 * (default 3): the current month plus that many full months are kept.
 */
@Service
public class AuditMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(AuditMaintenanceService.class);

    /** Partitions are created this many months ahead, so a missed night never matters. */
    private static final int MONTHS_AHEAD = 2;

    @PersistenceContext
    private EntityManager entityManager;

    private final int retentionMonths;

    public AuditMaintenanceService(@Value("${audit.retention-months:3}") int retentionMonths) {
        // Guard against a typo wiping the log: never keep less than one month.
        this.retentionMonths = Math.max(1, retentionMonths);
    }

    public int getRetentionMonths() {
        return retentionMonths;
    }

    /** Creates this month's and the next months' partitions if they are missing. */
    @Transactional
    public void ensurePartitions() {
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        for (int i = 0; i <= MONTHS_AHEAD; i++) {
            LocalDate month = firstOfMonth.plusMonths(i);
            Object created = entityManager
                    .createNativeQuery("SELECT audit.ensure_partition(CAST(:month AS date))")
                    .setParameter("month", month.toString())
                    .getSingleResult();
            if (Boolean.TRUE.equals(created)) {
                log.info("Audit: created partition for {}", month.toString().substring(0, 7));
            }
        }
    }

    /** Drops every monthly partition older than the retention window. Returns how many. */
    @Transactional
    public int dropExpiredPartitions() {
        Object dropped = entityManager
                .createNativeQuery("SELECT audit.drop_old_partitions(:months)")
                .setParameter("months", retentionMonths)
                .getSingleResult();
        int count = dropped instanceof Number number ? number.intValue() : 0;
        if (count > 0) {
            log.info("Audit: dropped {} expired partition(s); keeping {} month(s)", count, retentionMonths);
        }
        return count;
    }
}
