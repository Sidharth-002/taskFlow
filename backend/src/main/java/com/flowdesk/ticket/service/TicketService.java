package com.flowdesk.ticket.service;

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
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.UpdateTicketRequest;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.mapper.TicketMapper;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All methods here are only reachable for the four organization-scoped
 * roles - {@code TicketController}'s {@code @PreAuthorize} excludes
 * {@code SUPER_ADMIN} entirely, since tickets are an organization-scoped
 * resource and {@code SUPER_ADMIN} has none (see {@code User.organization}).
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketMapper ticketMapper;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            TicketMapper ticketMapper) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.ticketMapper = ticketMapper;
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

        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id, AuthenticatedPrincipal caller) {
        return ticketMapper.toResponse(loadVisible(id, caller));
    }

    /**
     * Default visibility per role (Section 6 of the spec) - one fixed
     * query per role rather than a single dynamic query, since combining
     * this with user-supplied filters cleanly is Phase 6's job (see
     * {@code TicketRepository}'s Javadoc).
     */
    @Transactional(readOnly = true)
    public Page<TicketResponse> list(Pageable pageable, AuthenticatedPrincipal caller) {
        Page<Ticket> page = switch (caller.role()) {
            case ORG_ADMIN -> ticketRepository.findByOrganizationId(caller.organizationId(), pageable);
            case TEAM_LEAD -> ticketRepository.findByOrganizationIdAndTeamTeamLeadId(
                    caller.organizationId(), caller.userId(), pageable);
            case AGENT -> ticketRepository.findByOrganizationIdAndAssignedToId(
                    caller.organizationId(), caller.userId(), pageable);
            case USER -> ticketRepository.findByOrganizationIdAndCreatedById(
                    caller.organizationId(), caller.userId(), pageable);
            case SUPER_ADMIN -> Page.empty(pageable); // unreachable - excluded by @PreAuthorize
        };
        return page.map(ticketMapper::toResponse);
    }

    /**
     * Backs both {@code PUT} and {@code PATCH} (see
     * {@code UpdateTicketRequest}'s Javadoc for why they share one
     * implementation). Reassignment ({@code teamId}/{@code assignedToId})
     * is restricted to {@code ORG_ADMIN}/{@code TEAM_LEAD} regardless of
     * which HTTP verb was used - the workflow and authorization rules live
     * here, not in the controller (Section 14 of the spec).
     */
    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request, AuthenticatedPrincipal caller) {
        Ticket ticket = loadVisible(id, caller);

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

        // saveAndFlush, not save: @Version and the @LastModifiedDate
        // auditing callback are only applied by Hibernate at flush time
        // (via dirty checking), not immediately when a setter is called.
        // Mapping to the response DTO before flushing would silently
        // return the ticket's *previous* version/updatedAt instead of the
        // values this update actually produced. Flushing here also means
        // a concurrent edit's ObjectOptimisticLockingFailureException
        // surfaces from this method call (handled globally), rather than
        // only later when the transaction commits.
        return ticketMapper.toResponse(ticketRepository.saveAndFlush(ticket));
    }

    @Transactional
    public void delete(Long id, AuthenticatedPrincipal caller) {
        Ticket ticket = ticketRepository.findByIdAndOrganizationId(id, caller.organizationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));
        ticketRepository.delete(ticket);
    }

    /**
     * Public (rather than the more common package-private for an internal
     * helper) specifically so {@code CommentService} - a comment thread is
     * always scoped to a ticket - can reuse the same tenant/visibility
     * rule instead of duplicating it. Returns the entity, not a DTO: this
     * is service-to-service collaboration within one request's
     * transaction, not a controller-facing API, so entity exposure here
     * doesn't violate the "never expose entities from controllers" rule.
     */
    @Transactional(readOnly = true)
    public Ticket loadVisible(Long id, AuthenticatedPrincipal caller) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Ticket", id));

        if (!ticket.getOrganization().getId().equals(caller.organizationId()) || !isVisibleToCaller(ticket, caller)) {
            // Same external behavior for "wrong organization" and "right
            // organization, but not within your role's visibility scope" -
            // both should look identical to a 404 from the caller's side.
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
            case SUPER_ADMIN -> false; // unreachable - excluded by @PreAuthorize
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
