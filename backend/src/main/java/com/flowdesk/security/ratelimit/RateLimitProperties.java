package com.flowdesk.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Bucket register, Bucket login, Bucket refresh) {

    public record Bucket(int capacity, Duration window) {
    }
}
