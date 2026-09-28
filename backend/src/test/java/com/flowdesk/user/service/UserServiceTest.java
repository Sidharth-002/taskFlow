package com.flowdesk.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.auth.service.AuthService;
import com.flowdesk.common.exception.DuplicateResourceException;
import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.user.dto.CreateUserRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthService authService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, organizationRepository, passwordEncoder, new UserMapper(), authService);
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

    private AuthenticatedPrincipal admin(Long id) {
        return new AuthenticatedPrincipal(id, ORG_ID, "admin@acme.test", Role.ORG_ADMIN);
    }

    @Test
    void create_rejectsSuperAdminRole() {
        var request = new CreateUserRequest("F", "L", "new@acme.test", "password123", Role.SUPER_ADMIN);

        assertThatThrownBy(() -> userService.create(request, admin(1L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_duplicateEmail_throws() {
        var request = new CreateUserRequest("F", "L", "dup@acme.test", "password123", Role.AGENT);
        when(userRepository.existsByEmail("dup@acme.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request, admin(1L)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_succeeds_scopedToCallerOrganization() {
        var request = new CreateUserRequest("F", "L", "new@acme.test", "password123", Role.AGENT);
        when(userRepository.existsByEmail("new@acme.test")).thenReturn(false);
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(org(ORG_ID));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setId(u, 99L);
            return u;
        });

        var response = userService.create(request, admin(1L));

        assertThat(response.role()).isEqualTo(Role.AGENT);
        assertThat(response.organizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void setActive_cannotChangeOwnStatus() {
        assertThatThrownBy(() -> userService.setActive(1L, false, admin(1L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).findByIdAndOrganizationId(any(), any());
    }

    @Test
    void setActive_deactivating_revokesAllRefreshTokens() {
        User target = user(2L, Role.AGENT);
        when(userRepository.findByIdAndOrganizationId(2L, ORG_ID)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.setActive(2L, false, admin(1L));

        assertThat(response.active()).isFalse();
        verify(authService).revokeAllTokensForUser(2L);
    }

    @Test
    void setActive_reactivating_doesNotRevokeTokens() {
        User target = user(2L, Role.AGENT);
        target.setActive(false);
        when(userRepository.findByIdAndOrganizationId(2L, ORG_ID)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.setActive(2L, true, admin(1L));

        assertThat(response.active()).isTrue();
        verify(authService, never()).revokeAllTokensForUser(any());
    }

    @Test
    void setActive_userInDifferentOrganization_throwsResourceNotFound() {
        when(userRepository.findByIdAndOrganizationId(2L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setActive(2L, false, admin(1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changeRole_cannotChangeOwnRole() {
        assertThatThrownBy(() -> userService.changeRole(1L, Role.AGENT, admin(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeRole_rejectsSuperAdmin() {
        assertThatThrownBy(() -> userService.changeRole(2L, Role.SUPER_ADMIN, admin(1L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).findByIdAndOrganizationId(any(), any());
    }

    @Test
    void changeRole_succeeds_andRevokesTokens() {
        User target = user(2L, Role.USER);
        when(userRepository.findByIdAndOrganizationId(2L, ORG_ID)).thenReturn(Optional.of(target));
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.changeRole(2L, Role.TEAM_LEAD, admin(1L));

        assertThat(response.role()).isEqualTo(Role.TEAM_LEAD);
        verify(authService).revokeAllTokensForUser(2L);
    }
}
