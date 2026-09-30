package com.flowdesk.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.entity.ProjectStatus;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.testsupport.IntegrationTest;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DomainEntityMappingIT {

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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@acme.test";
    }

    @Test
    void persistsFullDomainGraphWithRelationships() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme Inc").build());
        String adminEmail = uniqueEmail("admin");
        String agentEmail = uniqueEmail("agent");

        User admin = userRepository.save(User.builder()
                .organization(org)
                .email(adminEmail)
                .passwordHash("hashed")
                .firstName("Ada")
                .lastName("Admin")
                .role(Role.ORG_ADMIN)
                .build());

        User agent = userRepository.save(User.builder()
                .organization(org)
                .email(agentEmail)
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
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(reloaded.getOrganization().getId()).isEqualTo(org.getId());
        assertThat(reloaded.getProject().getName()).isEqualTo("Website");
        assertThat(reloaded.getTeam().getName()).isEqualTo("Support");
        assertThat(reloaded.getCreatedBy().getEmail()).isEqualTo(adminEmail);
        assertThat(reloaded.getAssignedTo().getEmail()).isEqualTo(agentEmail);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();

        Team reloadedTeam = teamRepository.findById(team.getId()).orElseThrow();
        assertThat(reloadedTeam.getMembers()).extracting(User::getEmail).containsExactly(agentEmail);
        assertThat(reloadedTeam.getTeamLead().getEmail()).isEqualTo(adminEmail);
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme Inc").build());
        String email = uniqueEmail("dup");
        userRepository.saveAndFlush(User.builder()
                .organization(org).email(email).passwordHash("x")
                .firstName("A").lastName("B").role(Role.USER).build());

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .organization(org).email(email).passwordHash("x")
                .firstName("C").lastName("D").role(Role.USER).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void superAdminHasNoOrganization() {
        User superAdmin = userRepository.saveAndFlush(User.builder()
                .organization(null)
                .email(uniqueEmail("platform"))
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
                .organization(org).email(uniqueEmail("creator")).passwordHash("x")
                .firstName("C").lastName("R").role(Role.USER).build());
        Project project = projectRepository.save(Project.builder().organization(org).name("P1").build());

        Ticket ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .organization(org).project(project).createdBy(user)
                .title("Something broke").build());
        Long versionAfterInsert = ticket.getVersion();

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        entityManager.flush();

        assertThat(ticket.getVersion()).isGreaterThan(versionAfterInsert);
    }
}
