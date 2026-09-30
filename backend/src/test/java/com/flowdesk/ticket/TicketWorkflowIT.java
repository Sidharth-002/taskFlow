package com.flowdesk.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@WebIntegrationTest
@Transactional
class TicketWorkflowIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private record OrgAndAdmin(String adminToken, Long organizationId) {
    }

    private OrgAndAdmin registerOrganization() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Acme " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new OrgAndAdmin(body.get("accessToken").asText(), body.get("user").get("organizationId").asLong());
    }

    private String createUser(String adminToken, String role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@acme.test";
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password123", "role", role))))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
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

    @Test
    void fullLifecycle_createAssignTransitionComment() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        String agentEmail = createUser(org.adminToken(), "AGENT");
        String agentToken = login(agentEmail);

        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Payment page 500", "projectId", projectId, "priority", "HIGH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult meResult = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + agentToken))
                .andReturn();
        long agentId = objectMapper.readTree(meResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assignedToId", agentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(agentId));

        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ticketId))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Investigating"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("F L"));

        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidTransition_openToClosedDirectly_returns409() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T", "projectId", projectId))))
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TICKET_TRANSITION"));
    }

    @Test
    void crossOrganizationAccess_returns404NotForbidden() throws Exception {
        OrgAndAdmin orgA = registerOrganization();
        OrgAndAdmin orgB = registerOrganization();
        long projectId = createProject(orgA.adminToken());
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + orgA.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T", "projectId", projectId))))
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/tickets/" + ticketId).header("Authorization", "Bearer " + orgB.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void agentCannotReassignTicket_returns403() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        String agentEmail = createUser(org.adminToken(), "AGENT");
        String agentToken = login(agentEmail);

        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T", "projectId", projectId))))
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + agentToken)).andReturn();
        long agentId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + org.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("assignedToId", agentId))));

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assignedToId", agentId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void plainUser_cannotUpdateOrDeleteTickets_butCanCreateAndComment() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        String userEmail = createUser(org.adminToken(), "USER");
        String userToken = login(userEmail);

        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "My issue", "projectId", projectId))))
                .andExpect(status().isCreated())
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tickets/" + ticketId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tickets/" + ticketId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Any update?"))))
                .andExpect(status().isCreated());
    }

    @Test
    void userSeesOnlyOwnTickets_notOtherUsersInSameOrg() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        String user1Email = createUser(org.adminToken(), "USER");
        String user2Email = createUser(org.adminToken(), "USER");
        String user1Token = login(user1Email);
        String user2Token = login(user2Email);

        mockMvc.perform(post("/api/tickets")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "User1's ticket", "projectId", projectId))));

        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void commentEdit_byNonAuthor_returns403() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        String userEmail = createUser(org.adminToken(), "USER");
        String userToken = login(userEmail);

        MvcResult ticket = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T", "projectId", projectId))))
                .andReturn();
        long ticketId = objectMapper.readTree(ticket.getResponse().getContentAsString()).get("id").asLong();

        MvcResult comment = mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Original"))))
                .andReturn();
        long commentId = objectMapper.readTree(comment.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "hacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + org.adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTicket_requiresOrgAdmin() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T", "projectId", projectId))))
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/tickets/" + ticketId).header("Authorization", "Bearer " + org.adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tickets/" + ticketId).header("Authorization", "Bearer " + org.adminToken()))
                .andExpect(status().isNotFound());
    }
}
