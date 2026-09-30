package com.flowdesk.security.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.testsupport.WebIntegrationTest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@WebIntegrationTest
@TestPropertySource(properties = {
        "app.rate-limit.register.capacity=2",
        "app.rate-limit.register.window=10s",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.login.window=10s"
})
@Transactional
class AuthRateLimitFilterIT {

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

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void register_exceedingCapacity_returns429() throws Exception {
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
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerPayload("ratelimit-indep-other-" + UUID.randomUUID() + "@acme.test")));
        }
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }
}
