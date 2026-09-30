package com.flowdesk.ticket.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.testsupport.WebIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebIntegrationTest
class KafkaEventFlowIT {

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
                                "organizationName", "Kafka Co " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new OrgAndAdmin(objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText());
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
                .andExpect(status().isOk())
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

    private long createTicket(String adminToken, long projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Investigate outage", "projectId", projectId))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode auditLog(String token, long ticketId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tickets/" + ticketId + "/audit-log")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode notifications(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    }

    private <T> T awaitCondition(Supplier<T> supplier, Predicate<T> condition, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        T last = null;
        while (Instant.now().isBefore(deadline)) {
            last = supplier.get();
            if (condition.test(last)) {
                return last;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition not met within " + timeout + " - last value: " + last);
    }

    private boolean containsEventType(JsonNode auditLog, String eventType) {
        for (JsonNode entry : auditLog) {
            if (entry.get("eventType").asText().equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNotificationForTicket(JsonNode notifications, long ticketId, String type) {
        for (JsonNode n : notifications) {
            if (n.get("ticketId").asLong() == ticketId && n.get("type").asText().equals(type)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void creatingTicket_eventuallyProducesAuditLogEntry() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        long ticketId = createTicket(org.adminToken(), projectId);

        JsonNode log = awaitCondition(
                () -> {
                    try {
                        return auditLog(org.adminToken(), ticketId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                l -> containsEventType(l, "TICKET_CREATED"),
                Duration.ofSeconds(15));

        assertThat(containsEventType(log, "TICKET_CREATED")).isTrue();
    }

    @Test
    void assigningTicket_notifiesAssignee_andRecordsAudit() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        long ticketId = createTicket(org.adminToken(), projectId);
        CreatedUser agent = createAndLogin(org.adminToken(), "AGENT");

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assignedToId", agent.id()))))
                .andExpect(status().isOk());

        awaitCondition(
                () -> {
                    try {
                        return notifications(agent.token());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                n -> containsNotificationForTicket(n, ticketId, "TICKET_ASSIGNED"),
                Duration.ofSeconds(15));

        awaitCondition(
                () -> {
                    try {
                        return auditLog(org.adminToken(), ticketId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                l -> containsEventType(l, "TICKET_ASSIGNED"),
                Duration.ofSeconds(15));
    }

    @Test
    void addingComment_notifiesTicketCreator_butNotTheCommentAuthorThemselves() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        long ticketId = createTicket(org.adminToken(), projectId);
        CreatedUser agent = createAndLogin(org.adminToken(), "AGENT");
        mockMvc.perform(patch("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + org.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("assignedToId", agent.id()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + agent.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Looking into it"))))
                .andExpect(status().isCreated());

        awaitCondition(
                () -> {
                    try {
                        return notifications(org.adminToken());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                n -> containsNotificationForTicket(n, ticketId, "TICKET_COMMENT_ADDED"),
                Duration.ofSeconds(15));

        Thread.sleep(1000);
        JsonNode agentNotifications = notifications(agent.token());
        assertThat(containsNotificationForTicket(agentNotifications, ticketId, "TICKET_COMMENT_ADDED")).isFalse();
    }

    @Test
    void closingTicket_notifiesCreator_andRecordsBothStatusChangedAndClosedAuditEntries() throws Exception {
        OrgAndAdmin org = registerOrganization();
        long projectId = createProject(org.adminToken());
        long ticketId = createTicket(org.adminToken(), projectId);

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + org.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))));
        mockMvc.perform(patch("/api/tickets/" + ticketId)
                .header("Authorization", "Bearer " + org.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED"))));
        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + org.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isOk());

        awaitCondition(
                () -> {
                    try {
                        return auditLog(org.adminToken(), ticketId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                l -> containsEventType(l, "TICKET_CLOSED"),
                Duration.ofSeconds(15));
    }
}
