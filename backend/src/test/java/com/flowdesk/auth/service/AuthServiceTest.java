package com.flowdesk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.RefreshRequest;
import com.flowdesk.auth.dto.RegisterRequest;
import com.flowdesk.auth.entity.RefreshToken;
import com.flowdesk.auth.exception.InvalidRefreshTokenException;
import com.flowdesk.auth.repository.RefreshTokenRepository;
import com.flowdesk.common.exception.DuplicateResourceException;
import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.JwtProperties;
import com.flowdesk.security.JwtService;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService}, with every collaborator mocked -
 * this exercises the service's own logic (workflow, exception mapping,
 * delegation to Spring Security) in isolation, complementing the full
 * stack coverage in {@code AuthControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private User buildUser(Long id, Organization org) {
        User user = User.builder()
                .organization(org)
                .email("ada@acme.test")
                .passwordHash("hashed")
                .firstName("Ada")
                .lastName("Admin")
                .role(Role.ORG_ADMIN)
                .build();
        setId(user, id);
        return user;
    }

    // BaseEntity's id is generated, not settable via a public API outside
    // persistence - tests build it through reflection to simulate "as
    // returned by the repository after save" without a real database.
    private void setId(Object entity, Long id) {
        try {
            var field = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Stubs shared only by the tests that actually reach token issuance. */
    private void stubTokenIssuance() {
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void register_createsOrganizationAndOrgAdmin_returnsTokens() {
        stubTokenIssuance();
        RegisterRequest request = new RegisterRequest("Acme Inc", "Ada", "Admin", "ada@acme.test", "password123");
        when(userRepository.existsByEmail("ada@acme.test")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        Organization savedOrg = Organization.builder().name("Acme Inc").build();
        setId(savedOrg, 10L);
        when(organizationRepository.save(any())).thenReturn(savedOrg);

        User savedUser = buildUser(1L, savedOrg);
        when(userRepository.save(any())).thenReturn(savedUser);

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(900L);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ORG_ADMIN);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(userCaptor.getValue().getOrganization()).isEqualTo(savedOrg);
    }

    @Test
    void register_duplicateEmail_throwsWithoutCreatingAnything() {
        RegisterRequest request = new RegisterRequest("Acme Inc", "Ada", "Admin", "ada@acme.test", "password123");
        when(userRepository.existsByEmail("ada@acme.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(organizationRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_delegatesToAuthenticationManager_returnsTokens() {
        stubTokenIssuance();
        LoginRequest request = new LoginRequest("ada@acme.test", "password123");
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        when(userRepository.findByEmail("ada@acme.test")).thenReturn(Optional.of(user));

        var response = authService.login(request);

        verify(authenticationManager).authenticate(
                eq(new UsernamePasswordAuthenticationToken("ada@acme.test", "password123")));
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void refresh_validToken_revokesOldAndIssuesNew() {
        stubTokenIssuance();
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        RefreshToken existing = RefreshToken.builder()
                .user(user)
                .tokenHash("irrelevant-in-this-test")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        var response = authService.refresh(new RefreshRequest("some-raw-token"));

        assertThat(existing.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void refresh_expiredToken_throwsInvalidRefreshTokenException() {
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        RefreshToken expired = RefreshToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_alreadyRevokedToken_throwsInvalidRefreshTokenException() {
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        RefreshToken revoked = RefreshToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("reused-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_concurrentRotationOfSameToken_translatesOptimisticLockFailure() {
        // Simulates the race this entity's @Version exists to close: this
        // request's own read saw the token as usable, but another request
        // won the race to rotate it first, so the flush below fails.
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(refreshTokenRepository.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(RefreshToken.class, 1L));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raced-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_unknownToken_throwsInvalidRefreshTokenException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("unknown-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_existingToken_revokesIt() {
        User user = buildUser(1L, Organization.builder().name("Acme").build());
        RefreshToken token = RefreshToken.builder()
                .user(user).tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(3600)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authService.logout(new RefreshRequest("some-token"));

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_unknownToken_doesNothingSilently() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout(new RefreshRequest("unknown-token"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void getCurrentUser_unknownId_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
