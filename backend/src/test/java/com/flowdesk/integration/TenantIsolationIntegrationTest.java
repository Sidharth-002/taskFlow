package com.flowdesk.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.testsupport.WebIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The spec's Section 5 requirement, made into a single, explicit,
 * auditable artifact: "Organization A cannot access Organization B's
 * data. This must be enforced server-side" - checked here systematically
 * across every organization-scoped resource type, in one place, rather
 * than left as incidental coverage scattered across each module's own
 * test class.
 *
 * <p>Every case follows the same shape: create a resource as Org A's
 * admin, then attempt to read/write it as Org B's admin, and assert a 404
 * (never a 403 - see the README's "why 404, not 403" ADR entry) that
 * doesn't distinguish "doesn't exist" from "not yours".
 */
@WebIntegrationTest
@Transactional
class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private record Org(String adminToken, long adminId) {
    }

    private Org registerOrganization() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Org " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Org(body.get("accessToken").asText(), body.get("user").get("id").asLong());
    }

    private long createProject(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Website"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTicket(String adminToken, long projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Issue", "projectId", projectId))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTeam(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Support"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void project_crossOrganizationRead_returns404() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long projectId = createProject(orgA.adminToken());

        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ticket_crossOrganizationRead_returns404() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long projectId = createProject(orgA.adminToken());
        long ticketId = createTicket(orgA.adminToken(), projectId);

        mockMvc.perform(get("/api/tickets/" + ticketId).header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ticket_crossOrganizationComment_returns404NotTheTicketsContent() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long projectId = createProject(orgA.adminToken());
        long ticketId = createTicket(orgA.adminToken(), projectId);

        // Org B shouldn't even learn the ticket exists in order to comment on it.
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + orgB.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "trying to comment"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void team_crossOrganizationRead_returns404() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long teamId = createTeam(orgA.adminToken());

        mockMvc.perform(get("/api/teams/" + teamId).header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void team_cannotAssignLeadFromAnotherOrganization() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long teamId = createTeam(orgA.adminToken());

        // Org A tries to make Org B's admin the lead of Org A's team -
        // the user lookup itself is organization-scoped, so this must
        // fail even though the team belongs to the caller's own org.
        mockMvc.perform(patch("/api/teams/" + teamId + "/lead")
                        .header("Authorization", "Bearer " + orgA.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", orgB.adminId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void user_crossOrganizationRead_returns404() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();

        mockMvc.perform(get("/api/users/" + orgA.adminId()).header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void user_list_neverLeaksOtherOrganizationsUsers() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)) // only Org B's own admin
                .andExpect(jsonPath("$.content[0].id").value(orgB.adminId()));
    }

    @Test
    void ticketCreation_rejectsProjectFromAnotherOrganization() throws Exception {
        Org orgA = registerOrganization();
        Org orgB = registerOrganization();
        long orgAsProjectId = createProject(orgA.adminToken());

        // Org B tries to create a ticket under Org A's project id.
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + orgB.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Sneaky", "projectId", orgAsProjectId))))
                .andExpect(status().isNotFound());
    }
}
