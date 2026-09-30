package com.flowdesk.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@WebIntegrationTest
@Transactional
class DashboardIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerOrgAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Dash Co " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private record CreatedUser(long id, String token) {
    }

    private CreatedUser createAndLogin(String adminToken, String role) throws Exception {
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
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        return new CreatedUser(id, token);
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

    private long createTeam(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Support"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createTicket(String token, long projectId, Long teamId) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", "Ticket");
        body.put("projectId", projectId);
        if (teamId != null) {
            body.put("teamId", teamId);
        }
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void orgAdmin_seesOrgWideCounts() throws Exception {
        String adminToken = registerOrgAdmin();
        long projectId = createProject(adminToken);
        createTicket(adminToken, projectId, null);
        createTicket(adminToken, projectId, null);

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(2))
                .andExpect(jsonPath("$.unassignedCount").value(2));
    }

    @Test
    void teamLead_seesOnlyTheirOwnTeamsTickets() throws Exception {
        String adminToken = registerOrgAdmin();
        long projectId = createProject(adminToken);
        long teamId = createTeam(adminToken);
        CreatedUser lead = createAndLogin(adminToken, "TEAM_LEAD");

        mockMvc.perform(patch("/api/teams/" + teamId + "/lead")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", lead.id()))));

        createTicket(adminToken, projectId, teamId);
        createTicket(adminToken, projectId, null);

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + lead.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(1));

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(2));
    }

    @Test
    void agentAndUser_areForbiddenFromTheDashboard() throws Exception {
        String adminToken = registerOrgAdmin();
        CreatedUser agent = createAndLogin(adminToken, "AGENT");
        CreatedUser user = createAndLogin(adminToken, "USER");

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + agent.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isForbidden());
    }
}
