package com.flowdesk.ticket.service;

import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.shared.exception.TenantAccessDeniedException;
import com.flowdesk.shared.exception.UnauthorizedOperationException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketListItemResponse;
import com.flowdesk.ticket.dto.TicketResponse;
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
import com.flowdesk.ticket.mapper.TicketMapper;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.ticket.spec.TicketSpecifications;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketMapper ticketMapper;
    private final TicketEventPublisher eventPublisher;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            TicketMapper ticketMapper,
            TicketEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.ticketMapper = ticketMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, AuthenticatedPrincipal caller) {
        Project project = projectRepository.findByIdAndOrganizationId(request.projectId(), caller.organizationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Project", request.projectId()));

        Team team = null;
        if (request.teamId() != null) {
            team = requireTeamInOrganization(request.teamId(), caller.organizationId());
        }

        User assignedTo = null;
        if (request.assignedToId() != null) {
            assignedTo = userRepository.findByIdAndOrganizationId(request.assignedToId(), caller.organizationId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User", request.assignedToId()));
        }

        Organization organization = organizationRepository.getReferenceById(caller.organizationId());
        User createdBy = userRepository.getReferenceById(caller.userId());

        Ticket ticket = Ticket.builder()
                .organization(organization)
                .project(project)
                .team(team)
                .createdBy(createdBy)
                .assignedTo(assignedTo)
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : TicketPriority.MEDIUM)
                .dueDate(request.dueDate())
                .build();

        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publish(new TicketCreatedEvent(
                saved.getId(), saved.getOrganization().getId(), saved.getProject().getId(),
                caller.userId(), saved.getTitle(), Instant.now()));
        return ticketMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id, AuthenticatedPrincipal caller) {
        return ticketMapper.toResponse(loadVisible(id, caller));
    }

    @Transactional(readOnly = true)
    public Page<TicketListItemResponse> list(TicketSearchCriteria criteria, Pageable pageable, AuthenticatedPrincipal caller) {
        List<Specification<Ticket>> specs = Stream.of(
                        TicketSpecifications.inOrganization(caller.organizationId()),
                        TicketSpecifications.visibleTo(caller),
                        TicketSpecifications.hasStatus(criteria.status()),
                        TicketSpecifications.hasPriority(criteria.priority()),
                        TicketSpecifications.hasProjectId(criteria.projectId()),
                        TicketSpecifications.hasTeamId(criteria.teamId()),
                        TicketSpecifications.hasAssignedToId(criteria.assignedToId()),
                        TicketSpecifications.titleContains(criteria.search()))
                .filter(Objects::nonNull)
                .toList();

        return ticketRepository.findAll(Specification.allOf(specs), pageable).map(ticketMapper::toListItem);
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request, AuthenticatedPrincipal caller) {
        Ticket ticket = loadVisible(id, caller);
        TicketStatus previousStatus = ticket.getStatus();
        Long previousAssigneeId = ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null;

        if (request.title() != null) {
            ticket.setTitle(request.title());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }
        if (request.status() != null && request.status() != ticket.getStatus()) {
            TicketWorkflow.validateTransition(ticket.getStatus(), request.status());
            ticket.setStatus(request.status());
        }

        if (request.teamId() != null || request.assignedToId() != null) {
            requireCanReassign(caller);
        }
        if (request.teamId() != null) {
            ticket.setTeam(requireTeamInOrganization(request.teamId(), caller.organizationId()));
        }
        if (request.assignedToId() != null) {
            User assignee = userRepository.findByIdAndOrganizationId(request.assignedToId(), caller.organizationId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User", request.assignedToId()));
            ticket.setAssignedTo(assignee);
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        publishUpdateEvents(saved, previousStatus, previousAssigneeId, caller);
        return ticketMapper.toResponse(saved);
    }

    private void publishUpdateEvents(Ticket ticket, TicketStatus previousStatus, Long previousAssigneeId, AuthenticatedPrincipal caller) {
        Instant now = Instant.now();

        Long newAssigneeId = ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null;
        if (newAssigneeId != null && !newAssigneeId.equals(previousAssigneeId)) {
            eventPublisher.publish(new TicketAssignedEvent(
                    ticket.getId(), ticket.getOrganization().getId(), newAssigneeId, caller.userId(), now));
        }

        if (ticket.getStatus() != previousStatus) {
            eventPublisher.publish(new TicketStatusChangedEvent(
                    ticket.getId(), ticket.getOrganization().getId(), previousStatus, ticket.getStatus(), caller.userId(), now));
            if (ticket.getStatus() == TicketStatus.CLOSED) {
                eventPublisher.publish(new TicketClosedEvent(
                        ticket.getId(), ticket.getOrganization().getId(), caller.userId(), now));
            }
        }
    }

    @Transactional
    public void delete(Long id, AuthenticatedPrincipal caller) {
        Ticket ticket = ticketRepository.findByIdAndOrganizationId(id, caller.organizationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));
        ticketRepository.delete(ticket);
    }

    @Transactional(readOnly = true)
    public Ticket loadVisible(Long id, AuthenticatedPrincipal caller) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));

        if (!ticket.getOrganization().getId().equals(caller.organizationId()) || !isVisibleToCaller(ticket, caller)) {
            throw new TenantAccessDeniedException(
                    "Ticket %d is not accessible to user %d".formatted(id, caller.userId()));
        }
        return ticket;
    }

    private boolean isVisibleToCaller(Ticket ticket, AuthenticatedPrincipal caller) {
        return switch (caller.role()) {
            case ORG_ADMIN -> true;
            case TEAM_LEAD -> ticket.getTeam() != null
                    && ticket.getTeam().getTeamLead() != null
                    && ticket.getTeam().getTeamLead().getId().equals(caller.userId());
            case AGENT -> ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(caller.userId());
            case USER -> ticket.getCreatedBy().getId().equals(caller.userId());
            case SUPER_ADMIN -> false;
        };
    }

    private void requireCanReassign(AuthenticatedPrincipal caller) {
        if (caller.role() != Role.ORG_ADMIN && caller.role() != Role.TEAM_LEAD) {
            throw new UnauthorizedOperationException("Only ORG_ADMIN or TEAM_LEAD may assign a ticket");
        }
    }

    private Team requireTeamInOrganization(Long teamId, Long organizationId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));
        if (!team.getOrganization().getId().equals(organizationId)) {
            throw new TenantAccessDeniedException("Team %d does not belong to organization %d".formatted(teamId, organizationId));
        }
        return team;
    }
}
