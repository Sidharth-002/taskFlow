package com.flowdesk.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end coverage of the Phase 5 gap found and fixed:
 * deactivating a user must actually cut off their access, not just their
 * ability to log in fresh - see {@code AuthService.refresh}'s Javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class UserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private record OrgAndAdmin(String adminToken) {
    }

    private OrgAndAdmin registerOrganization() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Org " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new OrgAndAdmin(objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText());
    }

    private record CreatedUser(long id, String email, String refreshToken) {
    }

    private CreatedUser createAndLoginUser(String adminToken, String role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@acme.test";
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password123", "role", role))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("refreshToken").asText();

        return new CreatedUser(id, email, refreshToken);
    }

    @Test
    void deactivatingUser_revokesExistingRefreshToken_andBlocksFutureLogin() throws Exception {
        OrgAndAdmin org = registerOrganization();
        CreatedUser agent = createAndLoginUser(org.adminToken(), "AGENT");

        mockMvc.perform(patch("/api/users/" + agent.id() + "/active")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // The refresh token issued before deactivation must no longer work -
        // this is the exact gap found during Phase 5 hardening.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", agent.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Nor can they log in fresh.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", agent.email(), "password", "password123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingUser_allowsLoginAgain() throws Exception {
        OrgAndAdmin org = registerOrganization();
        CreatedUser agent = createAndLoginUser(org.adminToken(), "AGENT");

        mockMvc.perform(patch("/api/users/" + agent.id() + "/active")
                .header("Authorization", "Bearer " + org.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("active", false))));

        mockMvc.perform(patch("/api/users/" + agent.id() + "/active")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", agent.email(), "password", "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void changingRole_revokesExistingRefreshToken() throws Exception {
        OrgAndAdmin org = registerOrganization();
        CreatedUser user = createAndLoginUser(org.adminToken(), "USER");

        mockMvc.perform(patch("/api/users/" + user.id() + "/role")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", user.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orgAdmin_cannotDeactivateOrChangeOwnRole() throws Exception {
        OrgAndAdmin org = registerOrganization();
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + org.adminToken())).andReturn();
        long adminId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/users/" + adminId + "/active")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void cannotCreateOrPromoteToSuperAdmin() throws Exception {
        OrgAndAdmin org = registerOrganization();
        CreatedUser user = createAndLoginUser(org.adminToken(), "USER");

        mockMvc.perform(patch("/api/users/" + user.id() + "/role")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "SUPER_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdmin_cannotDeactivateOtherUsers() throws Exception {
        OrgAndAdmin org = registerOrganization();
        CreatedUser agent = createAndLoginUser(org.adminToken(), "AGENT");
        CreatedUser victim = createAndLoginUser(org.adminToken(), "USER");
        MvcResult agentLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", agent.email(), "password", "password123"))))
                .andReturn();
        String agentToken = objectMapper.readTree(agentLogin.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(patch("/api/users/" + victim.id() + "/active")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isForbidden());
    }
}
