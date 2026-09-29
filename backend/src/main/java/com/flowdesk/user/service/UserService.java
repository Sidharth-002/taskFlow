package com.flowdesk.user.service;

import static com.flowdesk.config.CacheConfig.USERS_CACHE;

import com.flowdesk.auth.service.AuthService;
import com.flowdesk.shared.exception.DuplicateResourceException;
import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.user.dto.CreateUserRequest;
import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All mutating methods are {@code ORG_ADMIN}-only (enforced by
 * {@code @PreAuthorize} in {@code UserController}). Full self-service
 * profile editing and search are still left for a later pass - only the
 * admin actions needed to actually run an organization (add a colleague,
 * remove one's access, change their role) are implemented.
 *
 * <p>{@code getById} is cached (Phase 7, see {@code CacheConfig} and
 * {@code ProjectService}'s Javadoc for the caching rationale/key
 * convention). {@code setActive}/{@code changeRole} evict it -
 * particularly important here, more so than for Project/Team, since a
 * stale cached {@code active}/{@code role} value served back through
 * {@code GET /api/users/{id}} would visibly contradict the access that
 * was just revoked/changed (even though the JWT/refresh-token revocation
 * in {@code AuthService} is what actually enforces the change - this
 * eviction is about read-after-write consistency of this one endpoint,
 * not a security control by itself).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthService authService;

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuthService authService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @Transactional
    public UserSummaryResponse create(CreateUserRequest request, AuthenticatedPrincipal caller) {
        rejectSuperAdmin(request.role());
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Organization organization = organizationRepository.getReferenceById(caller.organizationId());
        User user = User.builder()
                .organization(organization)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(request.role())
                .build();

        return userMapper.toSummary(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> list(Pageable pageable, AuthenticatedPrincipal caller) {
        return userRepository.findByOrganizationId(caller.organizationId(), pageable)
                .map(userMapper::toSummary);
    }

    @Cacheable(cacheNames = USERS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional(readOnly = true)
    public UserSummaryResponse getById(Long id, AuthenticatedPrincipal caller) {
        return userRepository.findByIdAndOrganizationId(id, caller.organizationId())
                .map(userMapper::toSummary)
                // Tenant mismatch and "genuinely doesn't exist" are
                // indistinguishable here - the query itself already
                // filters by organization, so there's nothing further to
                // log server-side beyond the plain 404.
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    /**
     * Toggles active status either direction. Deactivating also revokes
     * every outstanding refresh token for that user (see
     * {@code AuthService.revokeAllTokensForUser}) - otherwise they could
     * keep refreshing into new sessions for up to the refresh token's
     * full lifetime after being deactivated (a gap found and closed
     * during Phase 5; see {@code AuthService.refresh}'s Javadoc).
     */
    @CacheEvict(cacheNames = USERS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public UserSummaryResponse setActive(Long id, boolean active, AuthenticatedPrincipal caller) {
        if (id.equals(caller.userId())) {
            throw new IllegalArgumentException("You cannot change your own active status");
        }
        User user = requireInOrganization(id, caller.organizationId());
        user.setActive(active);
        if (!active) {
            authService.revokeAllTokensForUser(user.getId());
        }
        return userMapper.toSummary(userRepository.saveAndFlush(user));
    }

    /**
     * Also revokes existing refresh tokens: a role change is a permission
     * change, and an already-issued access token would otherwise keep
     * carrying the *old* role claim until it naturally expires (see the
     * staleness trade-off documented on {@code AuthenticatedPrincipal}).
     * Forcing a fresh login/refresh under the new role closes that
     * window rather than accepting the full access-token lifetime of
     * delay on top of it.
     */
    @CacheEvict(cacheNames = USERS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public UserSummaryResponse changeRole(Long id, Role newRole, AuthenticatedPrincipal caller) {
        rejectSuperAdmin(newRole);
        if (id.equals(caller.userId())) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        User user = requireInOrganization(id, caller.organizationId());
        user.setRole(newRole);
        authService.revokeAllTokensForUser(user.getId());
        return userMapper.toSummary(userRepository.saveAndFlush(user));
    }

    private void rejectSuperAdmin(Role role) {
        if (role == Role.SUPER_ADMIN) {
            // Not a Bean Validation constraint because it's a rule about
            // these endpoints specifically, not a property of the Role
            // enum in isolation - SUPER_ADMIN is a legitimate value
            // elsewhere (it's just not assignable through organization
            // user management).
            throw new IllegalArgumentException("SUPER_ADMIN cannot be assigned through organization user management");
        }
    }

    private User requireInOrganization(Long id, Long organizationId) {
        return userRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
