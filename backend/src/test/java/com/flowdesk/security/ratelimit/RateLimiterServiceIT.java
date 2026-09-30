package com.flowdesk.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowdesk.testsupport.IntegrationTest;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@IntegrationTest
class RateLimiterServiceIT {

    @Autowired
    private RateLimiterService rateLimiterService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private String uniqueKey() {
        return "ratelimit:test:" + UUID.randomUUID();
    }

    @Test
    void allowsExactlyCapacityRequestsWithinTheWindow() {
        String key = uniqueKey();
        try {
            for (int i = 0; i < 5; i++) {
                assertThat(rateLimiterService.tryConsume(key, 5, Duration.ofSeconds(30))).isTrue();
            }
            assertThat(rateLimiterService.tryConsume(key, 5, Duration.ofSeconds(30))).isFalse();
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void resetsOnceTheWindowExpires() throws InterruptedException {
        String key = uniqueKey();
        try {
            assertThat(rateLimiterService.tryConsume(key, 1, Duration.ofSeconds(1))).isTrue();
            assertThat(rateLimiterService.tryConsume(key, 1, Duration.ofSeconds(1))).isFalse();

            Thread.sleep(1200);

            assertThat(rateLimiterService.tryConsume(key, 1, Duration.ofSeconds(1))).isTrue();
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void differentKeysAreIndependentCounters() {
        String keyA = uniqueKey();
        String keyB = uniqueKey();
        try {
            assertThat(rateLimiterService.tryConsume(keyA, 1, Duration.ofSeconds(30))).isTrue();
            assertThat(rateLimiterService.tryConsume(keyA, 1, Duration.ofSeconds(30))).isFalse();

            assertThat(rateLimiterService.tryConsume(keyB, 1, Duration.ofSeconds(30))).isTrue();
        } finally {
            redisTemplate.delete(Set.of(keyA, keyB));
        }
    }
}
