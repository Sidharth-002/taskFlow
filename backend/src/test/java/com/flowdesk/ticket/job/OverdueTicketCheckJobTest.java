package com.flowdesk.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.project.entity.Project;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.event.TicketEventPublisher;
import com.flowdesk.ticket.event.TicketOverdueEvent;
import com.flowdesk.ticket.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueTicketCheckJobTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketEventPublisher eventPublisher;

    private OverdueTicketCheckJob job;

    @BeforeEach
    void setUp() {
        job = new OverdueTicketCheckJob(ticketRepository, eventPublisher);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.flowdesk.shared.persistence.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Ticket overdueTicket(Long id, Long orgId, Instant dueDate) {
        Organization org = Organization.builder().name("Acme").build();
        setId(org, orgId);
        Project project = Project.builder().organization(org).name("P").build();
        setId(project, 100L);
        Ticket t = Ticket.builder()
                .organization(org)
                .project(project)
                .title("Overdue")
                .status(TicketStatus.OPEN)
                .dueDate(dueDate)
                .build();
        setId(t, id);
        return t;
    }

    @Test
    void run_publishesOverdueEventAndMarksEachTicketNotified_forEveryOverdueTicketFound() {
        Instant dueDate = Instant.now().minus(java.time.Duration.ofDays(1));
        Ticket t1 = overdueTicket(1L, 10L, dueDate);
        Ticket t2 = overdueTicket(2L, 20L, dueDate);
        when(ticketRepository.findOverdueAndNotYetNotified(any(), anyCollection())).thenReturn(List.of(t1, t2));

        job.run();

        var captor = ArgumentCaptor.forClass(TicketOverdueEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(TicketOverdueEvent::ticketId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getAllValues()).extracting(TicketOverdueEvent::organizationId).containsExactlyInAnyOrder(10L, 20L);

        assertThat(t1.getOverdueNotifiedAt()).isNotNull();
        assertThat(t2.getOverdueNotifiedAt()).isNotNull();
    }

    @Test
    void run_excludesResolvedAndClosedStatuses_inItsQuery() {
        when(ticketRepository.findOverdueAndNotYetNotified(any(), anyCollection())).thenReturn(List.of());

        job.run();

        var statusCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(ticketRepository).findOverdueAndNotYetNotified(any(), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(TicketStatus.RESOLVED, TicketStatus.CLOSED);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void run_noOverdueTickets_publishesNothing() {
        when(ticketRepository.findOverdueAndNotYetNotified(any(), anyCollection())).thenReturn(List.of());

        job.run();

        verify(eventPublisher, never()).publish(any());
    }
}
