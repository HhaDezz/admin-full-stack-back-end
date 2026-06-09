package com.atelier.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Async (audit rows are written in the background) and @Scheduled
 * (nightly audit housekeeping, revoked-token clean-up).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
