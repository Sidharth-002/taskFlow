package com.flowdesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.entity.ProjectStatus;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the core domain entities map correctly onto the Flyway-managed
 * schema: relationships persist and reload as expected, and the
 * constraints declared in the migrations (unique email, required foreign
 * keys) are actually enforced by the database rather than only assumed
 * from the Java model.
 *
 * <p>Runs against the docker-compose PostgreSQL instance (requires
 * {@code docker compose up -d postgres}); replaced by a Testcontainers-
 * backed base class once Phase 10 sets that infrastructure up.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional // each test rolls back, so tests don't leak data into each other
class DomainEntityMappingTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsFullDomainGraphWithRelationships() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme Inc").build());

        User admin = userRepository.save(User.builder()
                .organization(org)
                .email("admin@acme.test")
                .passwordHash("hashed")
                .firstName("Ada")
                .lastName("Admin")
                .role(Role.ORG_ADMIN)
                .build());

        User agent = userRepository.save(User.builder()
                .organization(org)
                .email("agent@acme.test")
                .passwordHash("hashed")
                .firstName("Alex")
                .lastName("Agent")
                .role(Role.AGENT)
                .build());

        Team team = Team.builder()
                .organization(org)
                .name("Support")
                .teamLead(admin)
                .build();
        team.addMember(agent);
        team = teamRepository.save(team);

        Project project = projectRepository.save(Project.builder()
                .organization(org)
                .name("Website")
                .status(ProjectStatus.ACTIVE)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .organization(org)
                .project(project)
                .team(team)
                .createdBy(admin)
                .assignedTo(agent)
                .title("Payment page throwing 500")
                .description("Users cannot check out")
                .priority(TicketPriority.HIGH)
                .build());

        entityManager.flush();
        entityManager.clear(); // force a real reload from the DB, not the 1st-level cache

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.OPEN); // default applied
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(reloaded.getOrganization().getId()).isEqualTo(org.getId());
        assertThat(reloaded.getProject().getName()).isEqualTo("Website");
        assertThat(reloaded.getTeam().getName()).isEqualTo("Support");
        assertThat(reloaded.getCreatedBy().getEmail()).isEqualTo("admin@acme.test");
        assertThat(reloaded.getAssignedTo().getEmail()).isEqualTo("agent@acme.test");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();

        Team reloadedTeam = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(reloadedTeam.getMembers()).extracting(User::getEmail).containsExactly("agent@acme.test");
        assertThat(reloadedTeam.getTeamLead().getEmail()).isEqualTo("admin@acme.test");
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme Inc").build());
        userRepository.saveAndFlush(User.builder()
                .organization(org).email("dup@acme.test").passwordHash("x")
                .firstName("A").lastName("B").role(Role.USER).build());

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .organization(org).email("dup@acme.test").passwordHash("x")
                .firstName("C").lastName("D").role(Role.USER).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void superAdminHasNoOrganization() {
        User superAdmin = userRepository.saveAndFlush(User.builder()
                .organization(null)
                .email("platform@flowdesk.test")
                .passwordHash("x")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .build());

        entityManager.clear();

        User reloaded = userRepository.findById(superAdmin.getId()).orElseThrow();
        assertThat(reloaded.getOrganization()).isNull();
    }

    @Test
    void ticketVersionIncrementsOnUpdate_dirtyCheckingDemonstration() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme Inc").build());
        User user = userRepository.save(User.builder()
                .organization(org).email("creator@acme.test").passwordHash("x")
                .firstName("C").lastName("R").role(Role.USER).build());
        Project project = projectRepository.save(Project.builder().organization(org).name("P1").build());

        Ticket ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .organization(org).project(project).createdBy(user)
                .title("Something broke").build());
        Long versionAfterInsert = ticket.getVersion();

        // No explicit repository.save() call here: Hibernate's dirty
        // checking detects the field change on this still-managed entity
        // and issues the UPDATE automatically at flush time.
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        entityManager.flush();

        assertThat(ticket.getVersion()).isGreaterThan(versionAfterInsert);
    }
}
