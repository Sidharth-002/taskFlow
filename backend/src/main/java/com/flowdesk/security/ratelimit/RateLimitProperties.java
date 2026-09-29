package com.flowdesk.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.rate-limit.*} (see {@code application.yml} for the
 * configured defaults and the rationale behind them). Registered via
 * {@code @ConfigurationPropertiesScan} on
 * {@link com.flowdesk.FlowDeskApplication}.
 *
 * @param register bucket for {@code POST /api/auth/register}
 * @param login bucket for {@code POST /api/auth/login}
 * @param refresh bucket for {@code POST /api/auth/refresh}
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Bucket register, Bucket login, Bucket refresh) {

    /**
     * A fixed-window limit: at most {@code capacity} requests from the
     * same client within any {@code window}-long period - see
     * {@link RateLimiterService}'s Javadoc for why fixed-window rather
     * than a more precise (and more complex) sliding window or token
     * bucket.
     */
    public record Bucket(int capacity, Duration window) {
    }
}
