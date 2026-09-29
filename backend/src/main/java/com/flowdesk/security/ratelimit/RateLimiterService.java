package com.flowdesk.security.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * A distributed fixed-window request counter backed by Redis, shared
 * across every application instance - the whole point of putting this in
 * Redis rather than an in-memory counter (e.g. Guava's {@code RateLimiter})
 * is that the limit holds even when FlowDesk is horizontally scaled behind
 * a load balancer, where a per-instance counter would let a client get
 * {@code capacity * instanceCount} requests through by hitting a different
 * instance each time.
 *
 * <p><b>Fixed window, not sliding window or token bucket.</b> A fixed
 * window allows a burst of up to {@code 2 * capacity} requests to slip
 * through right at a window boundary (capacity at the end of one window,
 * then capacity again at the start of the next, milliseconds later) - a
 * known, accepted imprecision. A sliding window or token bucket algorithm
 * closes that gap but needs either a sorted-set-per-request or a
 * more elaborate Lua script to stay atomic; for an auth-endpoint abuse
 * guard (not a hard billing/quota enforcement), the fixed window's
 * simplicity is worth that imprecision.
 *
 * <p><b>Atomicity.</b> {@code INCR} then conditionally {@code PEXPIRE}
 * only when the key was just created (count becomes 1) is done as a
 * single Lua script - not as two separate Redis calls - so that two
 * concurrent requests can never both observe count == 1 and both (or
 * neither) set the expiry, which would either leave the key without a
 * TTL (permanently denying that key once it reaches capacity) or reset
 * the window's start time on every request (a sliding window in
 * disguise, defeating the fixed-window design).
 */
@Service
public class RateLimiterService {

    private static final RedisScript<Long> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if tonumber(count) == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return {@code true} if this request is allowed (the caller has not
     * yet exceeded {@code capacity} requests within the current window for
     * this {@code key}), {@code false} if it must be rejected.
     */
    public boolean tryConsume(String key, int capacity, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_AND_EXPIRE, List.of(key), String.valueOf(window.toMillis()));
        return count != null && count <= capacity;
    }
}
