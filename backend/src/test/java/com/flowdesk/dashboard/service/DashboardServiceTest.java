package com.flowdesk.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.project.entity.Project;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private TicketRepository ticketRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(ticketRepository);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setTimestamps(Ticket ticket, Instant createdAt, Instant updatedAt) {
        try {
            var created = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("createdAt");
            created.setAccessible(true);
            created.set(ticket, createdAt);
            var updated = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("updatedAt");
            updated.setAccessible(true);
            updated.set(ticket, updatedAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Organization org(Long id) {
        Organization o = Organization.builder().name("Acme").build();
        setId(o, id);
        return o;
    }

    private User user(Long id) {
        User u = User.builder().organization(org(ORG_ID)).email("u" + id + "@acme.test")
                .passwordHash("x").firstName("F").lastName("L").role(Role.AGENT).build();
        setId(u, id);
        return u;
    }

    private Project project(Long id) {
        Project p = Project.builder().organization(org(ORG_ID)).name("P").build();
        setId(p, id);
        return p;
    }

    private AuthenticatedPrincipal principal(Long userId, Role role) {
        return new AuthenticatedPrincipal(userId, ORG_ID, "u" + userId + "@acme.test", role);
    }

    private Ticket ticket(Long id, TicketStatus status, TicketPriority priority, User assignedTo, Instant dueDate, Instant createdAt, Instant updatedAt) {
        Ticket t = Ticket.builder()
                .organization(org(ORG_ID))
                .project(project(100L))
                .createdBy(user(999L))
                .assignedTo(assignedTo)
                .title("T")
                .status(status)
                .priority(priority)
                .dueDate(dueDate)
                .build();
        setId(t, id);
        setTimestamps(t, createdAt, updatedAt);
        return t;
    }

    @Test
    void summary_aggregatesCountsAcrossStatusPriorityAssignmentAndTime() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);
        User agent = user(2L);

        List<Ticket> tickets = List.of(
                // Open, unassigned, overdue (due yesterday), created 2 days ago.
                ticket(1L, TicketStatus.OPEN, TicketPriority.HIGH, null, oneHourAgo.minus(1, ChronoUnit.DAYS), twoDaysAgo, twoDaysAgo),
                // In progress, assigned, not overdue (due in the future).
                ticket(2L, TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, agent, now.plus(5, ChronoUnit.DAYS), twoDaysAgo, twoDaysAgo),
                // Closed recently (within 7 days) - counts toward closedLastSevenDays.
                ticket(3L, TicketStatus.CLOSED, TicketPriority.LOW, agent, tenDaysAgo, tenDaysAgo, oneHourAgo),
                // Closed long ago - does NOT count toward closedLastSevenDays.
                ticket(4L, TicketStatus.CLOSED, TicketPriority.LOW, agent, tenDaysAgo, tenDaysAgo, tenDaysAgo),
                // Overdue due date, but RESOLVED - must not count as overdue.
                ticket(5L, TicketStatus.RESOLVED, TicketPriority.CRITICAL, agent, tenDaysAgo, tenDaysAgo, tenDaysAgo));

        when(ticketRepository.findAll(ArgumentMatchers.<Specification<Ticket>>any())).thenReturn(tickets);

        var summary = dashboardService.summary(principal(1L, Role.ORG_ADMIN));

        assertThat(summary.totalTickets()).isEqualTo(5);
        assertThat(summary.countsByStatus())
                .containsEntry(TicketStatus.OPEN, 1L)
                .containsEntry(TicketStatus.IN_PROGRESS, 1L)
                .containsEntry(TicketStatus.CLOSED, 2L)
                .containsEntry(TicketStatus.RESOLVED, 1L)
                .doesNotContainKey(TicketStatus.WAITING);
        assertThat(summary.countsByPriority())
                .containsEntry(TicketPriority.HIGH, 1L)
                .containsEntry(TicketPriority.MEDIUM, 1L)
                .containsEntry(TicketPriority.LOW, 2L)
                .containsEntry(TicketPriority.CRITICAL, 1L);
        assertThat(summary.unassignedCount()).isEqualTo(1);
        assertThat(summary.overdueCount()).isEqualTo(1); // only ticket 1 - RESOLVED/CLOSED never count as overdue
        assertThat(summary.createdLastSevenDays()).isEqualTo(2); // tickets 1 and 2
        assertThat(summary.closedLastSevenDays()).isEqualTo(1); // only ticket 3
    }

    @Test
    void summary_noTicketsInScope_returnsAllZeroes() {
        when(ticketRepository.findAll(ArgumentMatchers.<Specification<Ticket>>any())).thenReturn(List.of());

        var summary = dashboardService.summary(principal(1L, Role.ORG_ADMIN));

        assertThat(summary.totalTickets()).isZero();
        assertThat(summary.countsByStatus()).isEmpty();
        assertThat(summary.countsByPriority()).isEmpty();
        assertThat(summary.unassignedCount()).isZero();
        assertThat(summary.overdueCount()).isZero();
        assertThat(summary.createdLastSevenDays()).isZero();
        assertThat(summary.closedLastSevenDays()).isZero();
    }
}
