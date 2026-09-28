package com.flowdesk.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Read-through caching for lookups that are read far more often than they
 * change - a single project/team/user record fetched by ID, not a list or
 * search result (those stay uncached: they're either already
 * pagination-limited and cheap, or - Phase 6's ticket search - filtered
 * per request in a way that would fragment the cache into one entry per
 * filter combination for little benefit).
 *
 * <p>Only a single, shared {@link RedisCacheConfiguration} bean is
 * declared: Spring Boot's Redis cache autoconfiguration picks up exactly
 * one such bean as every cache's default configuration (a 10-minute TTL
 * here), so {@code @Cacheable("projects")}/{@code "teams"}/{@code "users"}
 * each get their own independent Redis keyspace (via the cache name
 * prefix) without needing one {@code RedisCacheManagerBuilderCustomizer}
 * entry per cache.
 *
 * <p>{@code GenericJackson2JsonRedisSerializer} with default typing
 * enabled is what lets one serializer handle every cached DTO type
 * (embedding the concrete class name in the JSON so deserialization knows
 * what to build) - the usual security caveat around default typing
 * (arbitrary class instantiation from untrusted JSON) doesn't apply here,
 * since the only thing ever deserialized is JSON this same application
 * wrote into Redis moments earlier, never external input.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PROJECTS_CACHE = "projects";
    public static final String TEAMS_CACHE = "teams";
    public static final String USERS_CACHE = "users";

    /**
     * Aggregated, expensive-to-compute reporting data (Phase 9's
     * {@code DashboardService}) - a much shorter TTL than the entity
     * caches above, and deliberately never evicted by
     * {@code @CacheEvict} on any ticket mutation. A dashboard is
     * inherently a snapshot, not a live view; a bounded staleness window
     * (see {@code dashboardCacheManagerBuilderCustomizer}) is the accepted
     * trade-off against wiring cache invalidation into every ticket
     * write path for a value nobody expects to be exactly real-time.
     */
    public static final String DASHBOARD_CACHE = "dashboard";

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return cacheConfiguration(Duration.ofMinutes(10));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer dashboardCacheManagerBuilderCustomizer() {
        return builder -> builder.withCacheConfiguration(DASHBOARD_CACHE, cacheConfiguration(Duration.ofMinutes(1)));
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // DefaultTyping.EVERYTHING, not NON_FINAL: every cached value here
        // is a Java `record` (ProjectResponse, TeamResponse, ...), and
        // records are implicitly final. NON_FINAL skips embedding type
        // info for final classes - fine for a direct
        // serialize/deserialize-as-the-same-type round trip, but
        // GenericJackson2JsonRedisSerializer always deserializes as
        // `Object.class` (it doesn't know the target type up front), which
        // needs that type info to know what to instantiate. Without it,
        // reading back a cached record throws instead of round-tripping -
        // this is also exactly what Spring Data Redis's own
        // GenericJackson2JsonRedisSerializer() no-arg constructor
        // configures internally, for the same reason.
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }
}
