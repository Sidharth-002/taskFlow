package com.flowdesk.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class TeamManagementIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerOrgAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Org " + UUID.randomUUID(),
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

    private long createTeam(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Support", "description", "Support team"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void createTeam_assignLead_addAndRemoveMembers() throws Exception {
        String adminToken = registerOrgAdmin();
        long teamId = createTeam(adminToken);
        CreatedUser lead = createAndLogin(adminToken, "TEAM_LEAD");
        CreatedUser member = createAndLogin(adminToken, "AGENT");

        mockMvc.perform(patch("/api/teams/" + teamId + "/lead")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", lead.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamLeadId").value(lead.id()));

        mockMvc.perform(post("/api/teams/" + teamId + "/members/" + member.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(member.id()));

        mockMvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/teams/" + teamId + "/members/" + member.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teamLead_canManageOwnTeamMembers_butNotAnotherTeams() throws Exception {
        String adminToken = registerOrgAdmin();
        long teamA = createTeam(adminToken);
        long teamB = createTeam(adminToken);
        CreatedUser leadA = createAndLogin(adminToken, "TEAM_LEAD");
        CreatedUser someUser = createAndLogin(adminToken, "USER");

        mockMvc.perform(patch("/api/teams/" + teamA + "/lead")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", leadA.id()))));

        mockMvc.perform(post("/api/teams/" + teamA + "/members/" + someUser.id())
                        .header("Authorization", "Bearer " + leadA.token()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/teams/" + teamB + "/members/" + someUser.id())
                        .header("Authorization", "Bearer " + leadA.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamLead_seesTheirTeamsTickets_throughTheRealApi() throws Exception {
        String adminToken = registerOrgAdmin();
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Website"))))
                .andReturn();
        long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString()).get("id").asLong();

        long teamId = createTeam(adminToken);
        CreatedUser lead = createAndLogin(adminToken, "TEAM_LEAD");
        mockMvc.perform(patch("/api/teams/" + teamId + "/lead")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", lead.id()))));

        MvcResult ticketResult = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Team ticket", "projectId", projectId, "teamId", teamId))))
                .andExpect(status().isCreated())
                .andReturn();
        long ticketId = objectMapper.readTree(ticketResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + lead.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ticketId));
    }
}
