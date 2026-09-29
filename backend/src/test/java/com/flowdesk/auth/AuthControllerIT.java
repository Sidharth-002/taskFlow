package com.flowdesk.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.testsupport.WebIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the full authentication flow through the real Spring Security
 * filter chain (registration, login, the JWT-protected {@code /me}
 * endpoint, refresh rotation, and logout) via MockMvc - as close to a real
 * HTTP client as a test gets without actually binding a port.
 *
 * <p>Runs against an ephemeral Testcontainers PostgreSQL instance (Phase
 * 10) - no local Docker state or manual setup required.
 */
@WebIntegrationTest
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@acme.test";
    }

    private String registerPayload(String email) throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of(
                "organizationName", "Acme Inc",
                "firstName", "Ada",
                "lastName", "Admin",
                "email", email,
                "password", "password123")));
    }

    @Test
    void register_returnsTokensAndCreatedUser() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("ORG_ADMIN"))
                // Never leak the password hash through the API, under any field name.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void register_invalidPayload_returns400WithFieldErrors() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "organizationName", "",
                "firstName", "Ada",
                "lastName", "Admin",
                "email", "not-an-email",
                "password", "short"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.organizationName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void login_wrongPassword_returns401WithoutRevealingWhichFieldFailed() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email)));

        String loginPayload = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "wrong-password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void me_withValidAccessToken_returnsProfile() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andReturn();
        JsonNode tokens = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ORG_ADMIN"));
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesToken_andRejectsReuseOfOldOne() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andReturn();
        JsonNode tokens = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String originalRefreshToken = tokens.get("refreshToken").asText();

        String refreshPayload = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", originalRefreshToken));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", not(originalRefreshToken)))
                .andReturn();
        JsonNode rotated = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = rotated.get("refreshToken").asText();

        // The old token was consumed by rotation - presenting it again must fail.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The new one, meanwhile, still works.
        String newRefreshPayload = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", newRefreshToken));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRefreshPayload))
                .andExpect(status().isOk());
    }

    @Test
    void logout_revokesToken_soSubsequentRefreshFails() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email)))
                .andReturn();
        JsonNode tokens = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String refreshToken = tokens.get("refreshToken").asText();
        String payload = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_unknownToken_stillReturns204() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", "not-a-real-token"));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent());
    }
}
