package com.flowdesk.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves {@code AuthRateLimitFilter} actually rejects requests once a
 * bucket is exhausted - not just that {@code RateLimiterService}'s Redis
 * script does the right arithmetic in isolation, but that it's wired into
 * the real filter chain ahead of the controller.
 *
 * <p>Overrides {@code app.rate-limit.*} to small, deterministic values via
 * {@code @TestPropertySource} - the defaults active for every other
 * integration test (see {@code application-dev.yml}) are deliberately too
 * generous to hit in a normal test run, so this class gets its own, much
 * lower limits (and, since that makes its configuration unique, its own
 * Spring context - a small extra startup cost worth paying for a
 * deterministic test).
 *
 * <p>Redis state from prior runs is flushed before each test: unlike this
 * project's other integration tests, a rate-limit counter isn't rolled
 * back by {@code @Transactional} (it lives in Redis, not the JPA
 * transaction), so without this a previous run's leftover count could
 * make an assertion here pass or fail for the wrong reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.rate-limit.register.capacity=2",
        "app.rate-limit.register.window=10s",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.login.window=10s"
})
@Transactional
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRateLimitState() {
        Set<String> keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String registerPayload(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "organizationName", "Rate Limit Co " + UUID.randomUUID(),
                "firstName", "Ada", "lastName", "Admin",
                "email", email, "password", "password123"));
    }

    @Test
    void login_exceedingCapacity_returns429WithRetryAfterAndDoesNotReachController() throws Exception {
        String email = "ratelimit-login-" + UUID.randomUUID() + "@acme.test";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)));

        String loginPayload = objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"));

        // Capacity is 3 - the first three logins go through normally...
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk());
        }

        // ...and the fourth, within the same window, is rejected before
        // ever reaching AuthService/the database - a wrong password here
        // would still 401, not 429, if the filter let it through.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void register_exceedingCapacity_returns429() throws Exception {
        // Capacity is 2.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerPayload("ratelimit-register-" + UUID.randomUUID() + "@acme.test")))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload("ratelimit-register-" + UUID.randomUUID() + "@acme.test")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void rateLimitBucketsAreIndependentPerEndpoint() throws Exception {
        String email = "ratelimit-indep-" + UUID.randomUUID() + "@acme.test";
        // Exhaust the register bucket (capacity 2).
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload("ratelimit-indep-other-" + UUID.randomUUID() + "@acme.test")));
        }
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andExpect(status().isTooManyRequests());

        // The login bucket (capacity 3) is untouched by the register
        // bucket being exhausted - a login attempt (even one that will
        // 401 for an unrelated reason: this email was never successfully
        // registered) still gets a genuine authentication response, not
        // a 429 borrowed from a different endpoint's counter.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }
}
