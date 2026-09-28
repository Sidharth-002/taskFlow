package com.flowdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods (Phase 9's {@code OverdueTicketCheckJob}
 * and {@code RefreshTokenCleanupJob}) - without this, their {@code @Scheduled}
 * annotations are silently never triggered.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
