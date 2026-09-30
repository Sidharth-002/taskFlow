package com.flowdesk.security.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

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

    public boolean tryConsume(String key, int capacity, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_AND_EXPIRE, List.of(key), String.valueOf(window.toMillis()));
        return count != null && count <= capacity;
    }
}
