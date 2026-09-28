package com.flowdesk.auth.job;

import com.flowdesk.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes expired refresh token rows - see
 * {@code RefreshTokenRepository.deleteExpiredBefore}'s Javadoc for why
 * this table needs a cleanup job at all (rotation and bulk revocation
 * both only ever set {@code revoked = true}, never delete). Runs on
 * {@code app.scheduling.refresh-token-cleanup-cron} (daily by default -
 * see {@code application.yml}).
 *
 * <p>{@code run()} is directly callable (not just reachable via the real
 * cron trigger) so tests can exercise it deterministically, the same
 * reasoning as {@code OverdueTicketCheckJob}.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "${app.scheduling.refresh-token-cleanup-cron}")
    @Transactional
    public void run() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
    }
}
