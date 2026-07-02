package com.atelier.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the audit housekeeping at startup and again every night at 02:15.
 *
 * Running it at startup as well matters: if the server was switched off over
 * a month end, the nightly run was missed and the new month would have no
 * partition to write into.
 *
 * It is a separate class from AuditMaintenanceService so that the calls go
 * through Spring's proxy and the @Transactional on those methods takes effect.
 */
@Component
public class AuditMaintenanceScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditMaintenanceScheduler.class);

    private final AuditMaintenanceService maintenance;

    public AuditMaintenanceScheduler(AuditMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public void run(ApplicationArguments args) {
        runMaintenance();
    }

    @Scheduled(cron = "0 15 2 * * *")
    public void nightly() {
        runMaintenance();
    }

    private void runMaintenance() {
        // Two separate try blocks: a failure to drop old months must not stop
        // the new month's partition being created, and the other way round.
        try {
            maintenance.ensurePartitions();
        } catch (Exception e) {
            log.error("Audit: could not create upcoming partitions", e);
        }
        try {
            maintenance.dropExpiredPartitions();
        } catch (Exception e) {
            log.error("Audit: could not drop expired partitions", e);
        }
    }
}
