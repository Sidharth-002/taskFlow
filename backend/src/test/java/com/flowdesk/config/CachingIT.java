package com.flowdesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.dto.ProjectResponse;
import com.flowdesk.project.dto.UpdateProjectRequest;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.project.service.ProjectService;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.dto.TeamResponse;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.team.service.TeamService;
import com.flowdesk.testsupport.IntegrationTest;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves {@code ProjectService}/{@code TeamService}'s {@code @Cacheable}
 * {@code getById} is a real read-through cache against real Redis, not
 * just an annotation that compiles - and that the corresponding
 * {@code @CacheEvict} on each mutating method actually invalidates it.
 * Mutating the underlying row directly through the repository (bypassing
 * the service, and therefore its eviction) is what proves the *cache* is
 * the thing serving the second read, rather than the database just
 * happening to still return the same value.
 *
 * <p>Uses the real, Spring-proxied service beans (not a unit test with a
 * mocked repository) specifically because {@code @Cacheable}/
 * {@code @CacheEvict} are AOP advice applied to the proxy - a unit test
 * that {@code new}s up the service directly would never exercise them at
 * all.
 */
@IntegrationTest
class CachingIT {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    @Transactional
    void projectGetById_isCached_andEvictedOnUpdate() {
        Organization org = organizationRepository.save(Organization.builder().name("Cache Co").build());
        Project project = projectRepository.save(Project.builder().organization(org).name("Original").build());
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal(1L, org.getId(), "admin@acme.test", Role.ORG_ADMIN);

        ProjectResponse first = projectService.getById(project.getId(), caller);
        assertThat(first.name()).isEqualTo("Original");

        // Mutate the row directly through the repository, bypassing
        // ProjectService entirely - if getById is genuinely cached, this
        // change is invisible until something evicts the entry.
        project.setName("Changed behind the cache's back");
        projectRepository.saveAndFlush(project);

        ProjectResponse stillCached = projectService.getById(project.getId(), caller);
        assertThat(stillCached.name())
                .as("a cached getById must not reflect a write that bypassed the service layer")
                .isEqualTo("Original");

        // Going through the service's own update method evicts the entry.
        projectService.update(project.getId(), new UpdateProjectRequest("Changed via service", null), caller);

        ProjectResponse afterEviction = projectService.getById(project.getId(), caller);
        assertThat(afterEviction.name()).isEqualTo("Changed via service");
    }

    @Test
    @Transactional
    void teamGetById_isCached_andEvictedOnAssignLead() {
        Organization org = organizationRepository.save(Organization.builder().name("Cache Co 2").build());
        Team team = teamRepository.save(Team.builder().organization(org).name("Support").build());
        User lead = userRepository.save(User.builder()
                .organization(org)
                .email("lead-" + UUID.randomUUID() + "@acme.test")
                .passwordHash("hashed")
                .firstName("Team")
                .lastName("Lead")
                .role(Role.TEAM_LEAD)
                .build());
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal(1L, org.getId(), "admin@acme.test", Role.ORG_ADMIN);

        TeamResponse first = teamService.getById(team.getId(), caller);
        assertThat(first.teamLeadId()).isNull();

        // Direct repository mutation, bypassing TeamService.assignLead
        // (and its @CacheEvict).
        team.setTeamLead(lead);
        teamRepository.saveAndFlush(team);

        TeamResponse stillCached = teamService.getById(team.getId(), caller);
        assertThat(stillCached.teamLeadId())
                .as("a cached getById must not reflect a write that bypassed the service layer")
                .isNull();

        // Going through the service's own assignLead method evicts the
        // entry (this call is a no-op business-wise, the lead is already
        // set at the DB level from the direct mutation above, but its
        // @CacheEvict is what matters here).
        teamService.assignLead(team.getId(), lead.getId(), caller);

        TeamResponse afterEviction = teamService.getById(team.getId(), caller);
        assertThat(afterEviction.teamLeadId()).isEqualTo(lead.getId());
    }
}
