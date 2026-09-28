package com.flowdesk.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.common.exception.TenantAccessDeniedException;
import com.flowdesk.common.exception.UnauthorizedOperationException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketSearchCriteria;
import com.flowdesk.ticket.dto.UpdateTicketRequest;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.event.TicketAssignedEvent;
import com.flowdesk.ticket.event.TicketClosedEvent;
import com.flowdesk.ticket.event.TicketCreatedEvent;
import com.flowdesk.ticket.event.TicketEventPublisher;
import com.flowdesk.ticket.event.TicketStatusChangedEvent;
import com.flowdesk.ticket.exception.InvalidTicketTransitionException;
import com.flowdesk.ticket.mapper.TicketMapper;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TicketEventPublisher eventPublisher;

    // A real instance, not a mock: TicketMapper is a small pure function
    // with no dependencies of its own, so exercising the real mapping
    // logic is more useful here than stubbing "toResponse returns X".
    // Built explicitly in setUp() rather than via @InjectMocks, since
    // Mockito's constructor-injection only auto-wires @Mock/@Spy fields -
    // it would otherwise silently pass null for this one.
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository, projectRepository, teamRepository, userRepository,
                organizationRepository, new TicketMapper(), eventPublisher);
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

    private Organization org(Long id) {
        Organization o = Organization.builder().name("Acme").build();
        setId(o, id);
        return o;
    }

    private User user(Long id, Role role) {
        User u = User.builder().organization(org(ORG_ID)).email("u" + id + "@acme.test")
                .passwordHash("x").firstName("F").lastName("L").role(role).build();
        setId(u, id);
        return u;
    }

    private AuthenticatedPrincipal principal(Long userId, Role role) {
        return new AuthenticatedPrincipal(userId, ORG_ID, "u" + userId + "@acme.test", role);
    }

    private Project project(Long id, Long orgId) {
        Project p = Project.builder().organization(org(orgId)).name("P").build();
        setId(p, id);
        return p;
    }

    private Ticket ticket(Long id, Long orgId, User createdBy, User assignedTo, Team team, TicketStatus status) {
        Ticket t = Ticket.builder()
                .organization(org(orgId))
                .project(project(100L, orgId))
                .team(team)
                .createdBy(createdBy)
                .assignedTo(assignedTo)
                .title("T")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .build();
        setId(t, id);
        return t;
    }

    // ---- create ----

    @Test
    void create_defaultsPriorityToMedium_andStatusToOpen() {
        AuthenticatedPrincipal caller = principal(1L, Role.ORG_ADMIN);
        Project project = project(10L, ORG_ID);
        when(projectRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.of(project));
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(org(ORG_ID));
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L, Role.ORG_ADMIN));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            setId(t, 500L);
            return t;
        });

        var response = ticketService.create(
                new CreateTicketRequest("Broken thing", "desc", null, 10L, null, null, null), caller);

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.priority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void create_publishesTicketCreatedEvent() {
        AuthenticatedPrincipal caller = principal(1L, Role.ORG_ADMIN);
        Project project = project(10L, ORG_ID);
        when(projectRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.of(project));
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(org(ORG_ID));
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L, Role.ORG_ADMIN));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            setId(t, 500L);
            return t;
        });

        ticketService.create(new CreateTicketRequest("Broken thing", "desc", null, 10L, null, null, null), caller);

        var captor = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().ticketId()).isEqualTo(500L);
        assertThat(captor.getValue().organizationId()).isEqualTo(ORG_ID);
        assertThat(captor.getValue().createdById()).isEqualTo(1L);
        assertThat(captor.getValue().title()).isEqualTo("Broken thing");
    }

    @Test
    void create_projectInDifferentOrganization_throwsResourceNotFound() {
        AuthenticatedPrincipal caller = principal(1L, Role.ORG_ADMIN);
        when(projectRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(
                new CreateTicketRequest("T", null, null, 10L, null, null, null), caller))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- visibility (getById / list) ----

    @Test
    void getById_orgAdmin_seesAnyTicketInOrganization() {
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        var response = ticketService.getById(5L, principal(1L, Role.ORG_ADMIN));

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void getById_differentOrganization_throwsTenantAccessDenied() {
        Ticket ticket = ticket(5L, 999L, user(2L, Role.USER), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getById(5L, principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void getById_userNotTheCreator_isNotVisible() {
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getById(5L, principal(99L, Role.USER)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void getById_userIsTheCreator_isVisible() {
        User creator = user(2L, Role.USER);
        Ticket ticket = ticket(5L, ORG_ID, creator, null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        var response = ticketService.getById(5L, principal(2L, Role.USER));

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void getById_agentNotAssigned_isNotVisible() {
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), user(3L, Role.AGENT), null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getById(5L, principal(99L, Role.AGENT)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void getById_agentAssigned_isVisible() {
        User agent = user(3L, Role.AGENT);
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), agent, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        var response = ticketService.getById(5L, principal(3L, Role.AGENT));

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void getById_teamLeadOfTicketsTeam_isVisible() {
        User lead = user(7L, Role.TEAM_LEAD);
        Team team = Team.builder().organization(org(ORG_ID)).name("Support").teamLead(lead).build();
        setId(team, 20L);
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), null, team, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        var response = ticketService.getById(5L, principal(7L, Role.TEAM_LEAD));

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void getById_teamLeadOfDifferentTeam_isNotVisible() {
        User someoneElse = user(8L, Role.TEAM_LEAD);
        Team team = Team.builder().organization(org(ORG_ID)).name("Support").teamLead(someoneElse).build();
        setId(team, 20L);
        Ticket ticket = ticket(5L, ORG_ID, user(2L, Role.USER), null, team, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getById(5L, principal(7L, Role.TEAM_LEAD)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void list_delegatesToSpecificationBasedFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.findAll(ArgumentMatchers.<Specification<Ticket>>any(), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        ticketService.list(new TicketSearchCriteria(null, null, null, null, null, null), pageable, principal(2L, Role.USER));

        verify(ticketRepository).findAll(ArgumentMatchers.<Specification<Ticket>>any(), eq(pageable));
    }

    // ---- update: workflow + reassignment authorization ----

    @Test
    void update_invalidTransition_throwsAndDoesNotPersist() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(
                5L, new UpdateTicketRequest(null, null, TicketStatus.CLOSED, null, null, null, null),
                principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(InvalidTicketTransitionException.class);

        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_validTransition_applies() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = ticketService.update(
                5L, new UpdateTicketRequest(null, null, TicketStatus.IN_PROGRESS, null, null, null, null),
                principal(1L, Role.ORG_ADMIN));

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void update_statusChange_publishesTicketStatusChangedEvent_butNotWhenUnchanged() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.update(
                5L, new UpdateTicketRequest(null, null, TicketStatus.IN_PROGRESS, null, null, null, null),
                principal(1L, Role.ORG_ADMIN));

        var captor = ArgumentCaptor.forClass(TicketStatusChangedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().oldStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(captor.getValue().newStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(eventPublisher, never()).publish(any(TicketClosedEvent.class));

        // A no-op "update" that doesn't actually change status (e.g. only
        // the title changes) must not publish a spurious status-change
        // event.
        clearInvocations(eventPublisher);
        ticketService.update(
                5L, new UpdateTicketRequest("New title", null, null, null, null, null, null),
                principal(1L, Role.ORG_ADMIN));
        verify(eventPublisher, never()).publish(any(TicketStatusChangedEvent.class));
    }

    @Test
    void update_transitionToClosed_publishesBothStatusChangedAndClosedEvents() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.RESOLVED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.update(
                5L, new UpdateTicketRequest(null, null, TicketStatus.CLOSED, null, null, null, null),
                principal(1L, Role.ORG_ADMIN));

        verify(eventPublisher).publish(any(TicketStatusChangedEvent.class));
        var captor = ArgumentCaptor.forClass(TicketClosedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().ticketId()).isEqualTo(5L);
        assertThat(captor.getValue().closedById()).isEqualTo(1L);
    }

    @Test
    void update_agentReassigningTicket_throwsUnauthorizedOperation() {
        User agent = user(3L, Role.AGENT);
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), agent, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(
                5L, new UpdateTicketRequest(null, null, null, null, null, 3L, null),
                principal(3L, Role.AGENT)))
                .isInstanceOf(UnauthorizedOperationException.class);

        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_orgAdminReassigningTicket_succeeds() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.OPEN);
        User newAssignee = user(9L, Role.AGENT);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndOrganizationId(9L, ORG_ID)).thenReturn(Optional.of(newAssignee));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = ticketService.update(
                5L, new UpdateTicketRequest(null, null, null, null, null, 9L, null),
                principal(1L, Role.ORG_ADMIN));

        assertThat(response.assignedToId()).isEqualTo(9L);

        var captor = ArgumentCaptor.forClass(TicketAssignedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().assignedToId()).isEqualTo(9L);
        assertThat(captor.getValue().assignedById()).isEqualTo(1L);
    }

    @Test
    void update_reassigningToSameAssignee_doesNotPublishTicketAssignedEvent() {
        User existingAssignee = user(9L, Role.AGENT);
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), existingAssignee, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndOrganizationId(9L, ORG_ID)).thenReturn(Optional.of(existingAssignee));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.update(
                5L, new UpdateTicketRequest(null, null, null, null, null, 9L, null),
                principal(1L, Role.ORG_ADMIN));

        verify(eventPublisher, never()).publish(any(TicketAssignedEvent.class));
    }

    @Test
    void update_agentChangingOwnAssignedTicketStatus_succeedsWithoutReassigning() {
        User agent = user(3L, Role.AGENT);
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), agent, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = ticketService.update(
                5L, new UpdateTicketRequest(null, null, TicketStatus.IN_PROGRESS, null, null, null, null),
                principal(3L, Role.AGENT));

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    // ---- delete ----

    @Test
    void delete_removesTenantScopedTicket() {
        Ticket ticket = ticket(5L, ORG_ID, user(1L, Role.ORG_ADMIN), null, null, TicketStatus.OPEN);
        when(ticketRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(ticket));

        ticketService.delete(5L, principal(1L, Role.ORG_ADMIN));

        verify(ticketRepository).delete(ticket);
    }

    @Test
    void delete_ticketInDifferentOrganization_throwsResourceNotFound() {
        when(ticketRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.delete(5L, principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
