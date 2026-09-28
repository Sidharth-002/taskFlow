package com.flowdesk.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.common.exception.TenantAccessDeniedException;
import com.flowdesk.common.exception.UnauthorizedOperationException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.dto.CreateTeamRequest;
import com.flowdesk.team.entity.Team;
import com.flowdesk.team.mapper.TeamMapper;
import com.flowdesk.team.repository.TeamRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, organizationRepository, userRepository, new TeamMapper(), new UserMapper());
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

    private User user(Long id, Long orgId, Role role) {
        User u = User.builder().organization(org(orgId)).email("u" + id + "@acme.test")
                .passwordHash("x").firstName("F").lastName("L").role(role).build();
        setId(u, id);
        return u;
    }

    private Team team(Long id, Long orgId, User teamLead) {
        Team t = Team.builder().organization(org(orgId)).name("Support").teamLead(teamLead).build();
        setId(t, id);
        return t;
    }

    private AuthenticatedPrincipal principal(Long userId, Role role) {
        return new AuthenticatedPrincipal(userId, ORG_ID, "u" + userId + "@acme.test", role);
    }

    @Test
    void create_scopesToCallerOrganization() {
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(org(ORG_ID));
        when(teamRepository.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            setId(t, 10L);
            return t;
        });

        var response = teamService.create(new CreateTeamRequest("Support", "desc"), principal(1L, Role.ORG_ADMIN));

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.name()).isEqualTo("Support");
    }

    @Test
    void getById_differentOrganization_throwsTenantAccessDenied() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team(10L, 999L, null)));

        assertThatThrownBy(() -> teamService.getById(10L, principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Team team = team(10L, ORG_ID, null);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = teamService.deactivate(10L, principal(1L, Role.ORG_ADMIN));

        assertThat(response.active()).isFalse();
    }

    @Test
    void assignLead_userInDifferentOrganization_throwsResourceNotFound() {
        Team team = team(10L, ORG_ID, null);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.assignLead(10L, 5L, principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignLead_succeeds() {
        Team team = team(10L, ORG_ID, null);
        User newLead = user(5L, ORG_ID, Role.TEAM_LEAD);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(newLead));
        when(teamRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = teamService.assignLead(10L, 5L, principal(1L, Role.ORG_ADMIN));

        assertThat(response.teamLeadId()).isEqualTo(5L);
    }

    @Test
    void addMember_byOrgAdmin_succeeds() {
        Team team = team(10L, ORG_ID, null);
        User newMember = user(5L, ORG_ID, Role.USER);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(newMember));
        when(teamRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var members = teamService.addMember(10L, 5L, principal(1L, Role.ORG_ADMIN));

        assertThat(members).extracting("id").containsExactly(5L);
    }

    @Test
    void addMember_byThisTeamsLead_succeeds() {
        User lead = user(7L, ORG_ID, Role.TEAM_LEAD);
        Team team = team(10L, ORG_ID, lead);
        User newMember = user(5L, ORG_ID, Role.USER);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(newMember));
        when(teamRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var members = teamService.addMember(10L, 5L, principal(7L, Role.TEAM_LEAD));

        assertThat(members).extracting("id").containsExactly(5L);
    }

    @Test
    void addMember_byDifferentTeamsLead_throwsUnauthorizedOperation() {
        User someoneElsesLead = user(8L, ORG_ID, Role.TEAM_LEAD);
        Team team = team(10L, ORG_ID, someoneElsesLead);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.addMember(10L, 5L, principal(7L, Role.TEAM_LEAD)))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void removeMember_byOrgAdmin_succeeds() {
        User member = user(5L, ORG_ID, Role.USER);
        Team team = team(10L, ORG_ID, null);
        team.addMember(member);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndOrganizationId(5L, ORG_ID)).thenReturn(Optional.of(member));

        teamService.removeMember(10L, 5L, principal(1L, Role.ORG_ADMIN));

        assertThat(team.getMembers()).isEmpty();
    }

    @Test
    void listMembers_returnsCurrentMembers() {
        User member = user(5L, ORG_ID, Role.USER);
        Team team = team(10L, ORG_ID, null);
        team.addMember(member);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

        var members = teamService.listMembers(10L, principal(1L, Role.ORG_ADMIN));

        assertThat(members).extracting("id").containsExactly(5L);
    }
}
