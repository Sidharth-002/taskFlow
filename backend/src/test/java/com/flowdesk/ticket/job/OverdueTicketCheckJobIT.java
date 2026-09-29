package com.flowdesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.testsupport.WebIntegrationTest;
import com.flowdesk.ticket.job.OverdueTicketCheckJob;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves {@code OverdueTicketCheckJob} end-to-end through the real Kafka
 * pipeline: a ticket already past its due date gets a
 * {@code TicketOverdueEvent} published, consumed into both a
 * {@code Notification} and an {@code AuditLog} row, and is never
 * re-notified on a second run.
 *
 * <p>{@code job.run()} is called directly rather than waiting for its real
 * cron trigger - deterministic and immediate, and exactly what the job's
 * own Javadoc says it's designed for. Not {@code @Transactional} for the
 * same reason as {@code KafkaEventFlowIntegrationTest}: the job's
 * {@code @Transactional} method needs to actually commit for
 * {@code TicketEventKafkaRelay} (which only relays {@code AFTER_COMMIT})
 * to fire.
 */
@WebIntegrationTest
class OverdueTicketCheckJobIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OverdueTicketCheckJob overdueTicketCheckJob;

    private String registerOrgAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", "Overdue Co " + UUID.randomUUID(),
                                "firstName", "Ada", "lastName", "Admin",
                                "email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Website"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createOverdueTicket(String token, long projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Fix the outage", "projectId", projectId,
                                "dueDate", Instant.now().minus(Duration.ofDays(1)).toString()))))
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

    private long countOfType(JsonNode nodes, String field, String value) {
        long count = 0;
        for (JsonNode n : nodes) {
            if (n.get(field).asText().equals(value)) {
                count++;
            }
        }
        return count;
    }

    private <T> T awaitCondition(java.util.function.Supplier<T> supplier, java.util.function.Predicate<T> condition, Duration timeout) throws Exception {
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

    @Test
    void run_publishesOverdueEvent_notifiesCreator_andDoesNotReNotifyOnASecondRun() throws Exception {
        String adminToken = registerOrgAdmin();
        long projectId = createProject(adminToken);
        long ticketId = createOverdueTicket(adminToken, projectId);

        overdueTicketCheckJob.run();

        awaitCondition(
                () -> {
                    try {
                        return auditLog(adminToken, ticketId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                l -> countOfType(l, "eventType", "TICKET_OVERDUE") == 1,
                Duration.ofSeconds(15));

        awaitCondition(
                () -> {
                    try {
                        return notifications(adminToken);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                n -> countOfType(n, "type", "TICKET_OVERDUE") == 1,
                Duration.ofSeconds(15));

        // A second run must not re-publish for the same ticket -
        // overdueNotifiedAt was set on the first run.
        overdueTicketCheckJob.run();
        Thread.sleep(1000); // give a (correctly not-happening) second event a moment, were it to occur
        assertThat(countOfType(auditLog(adminToken, ticketId), "eventType", "TICKET_OVERDUE")).isEqualTo(1);
        assertThat(countOfType(notifications(adminToken), "type", "TICKET_OVERDUE")).isEqualTo(1);
    }
}
