package com.flowdesk.team.service;

import static com.flowdesk.config.CacheConfig.TEAMS_CACHE;

import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.shared.exception.TenantAccessDeniedException;
import com.flowdesk.shared.exception.UnauthorizedOperationException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.dto.CreateTeamRequest;
import com.flowdesk.team.dto.TeamResponse;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.mapper.TeamMapper;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create/update/deactivate/assign-lead are {@code ORG_ADMIN}-only
 * (enforced in {@code TeamController}). Adding/removing members is the
 * one action also open to the team's own {@code TEAM_LEAD} (Section 6:
 * "Manage team members") - a row-level check done here, the same pattern
 * as ticket reassignment authorization in {@code TicketService}.
 *
 * <p>{@code getById} is cached (Phase 7, see {@code CacheConfig} and
 * {@code ProjectService}'s Javadoc for the caching rationale/key
 * convention this follows). {@code addMember}/{@code removeMember}
 * deliberately do <em>not</em> evict {@code TEAMS_CACHE} - they change
 * {@code Team.members}, but {@link TeamResponse} doesn't expose the
 * member list at all (see {@code listMembers}, which is separate and
 * uncached), so the cached response stays accurate.
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;

    public TeamService(
            TeamRepository teamRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            TeamMapper teamMapper,
            UserMapper userMapper) {
        this.teamRepository = teamRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public TeamResponse create(CreateTeamRequest request, AuthenticatedPrincipal caller) {
        Organization organization = organizationRepository.getReferenceById(caller.organizationId());
        Team team = Team.builder()
                .organization(organization)
                .name(request.name())
                .description(request.description())
                .build();
        return teamMapper.toResponse(teamRepository.save(team));
    }

    @Cacheable(cacheNames = TEAMS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional(readOnly = true)
    public TeamResponse getById(Long id, AuthenticatedPrincipal caller) {
        return teamMapper.toResponse(loadTenantScoped(id, caller.organizationId()));
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> list(Pageable pageable, AuthenticatedPrincipal caller) {
        return teamRepository.findByOrganizationId(caller.organizationId(), pageable)
                .map(teamMapper::toResponse);
    }

    @CacheEvict(cacheNames = TEAMS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public TeamResponse update(Long id, CreateTeamRequest request, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        team.setName(request.name());
        team.setDescription(request.description());
        return teamMapper.toResponse(teamRepository.saveAndFlush(team));
    }

    @CacheEvict(cacheNames = TEAMS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public TeamResponse deactivate(Long id, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        team.setActive(false);
        return teamMapper.toResponse(teamRepository.saveAndFlush(team));
    }

    @CacheEvict(cacheNames = TEAMS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public TeamResponse assignLead(Long id, Long userId, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        User user = requireUserInOrganization(userId, caller.organizationId());
        team.setTeamLead(user);
        return teamMapper.toResponse(teamRepository.saveAndFlush(team));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listMembers(Long id, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        return team.getMembers().stream().map(userMapper::toSummary).toList();
    }

    @Transactional
    public List<UserSummaryResponse> addMember(Long id, Long userId, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        requireCanManageMembers(team, caller);
        User user = requireUserInOrganization(userId, caller.organizationId());

        team.addMember(user);
        teamRepository.saveAndFlush(team);
        return team.getMembers().stream().map(userMapper::toSummary).toList();
    }

    @Transactional
    public void removeMember(Long id, Long userId, AuthenticatedPrincipal caller) {
        Team team = loadTenantScoped(id, caller.organizationId());
        requireCanManageMembers(team, caller);
        User user = requireUserInOrganization(userId, caller.organizationId());

        team.removeMember(user);
        teamRepository.save(team);
    }

    private void requireCanManageMembers(Team team, AuthenticatedPrincipal caller) {
        boolean isOrgAdmin = caller.role() == Role.ORG_ADMIN;
        boolean isThisTeamsLead = caller.role() == Role.TEAM_LEAD
                && team.getTeamLead() != null
                && team.getTeamLead().getId().equals(caller.userId());
        if (!isOrgAdmin && !isThisTeamsLead) {
            throw new UnauthorizedOperationException("Only ORG_ADMIN or this team's lead may manage its members");
        }
    }

    private User requireUserInOrganization(Long userId, Long organizationId) {
        return userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private Team loadTenantScoped(Long id, Long organizationId) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", id));
        if (!team.getOrganization().getId().equals(organizationId)) {
            throw new TenantAccessDeniedException(
                    "Team %d does not belong to organization %d".formatted(id, organizationId));
        }
        return team;
    }
}
