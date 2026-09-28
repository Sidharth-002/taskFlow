package com.flowdesk.user.service;

import com.flowdesk.common.exception.DuplicateResourceException;
import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.user.dto.CreateUserRequest;
import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mostly read-only: full self-service user management (deactivate,
 * change role, search) is left for a later pass. {@link #create} is the
 * one exception, added once it became clear during Phase 4 verification
 * that without it, an organization can never have more than the single
 * {@code ORG_ADMIN} who registered it - making ticket assignment and
 * role-based visibility impossible to exercise at all, not just
 * inconvenient to test.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /** {@code ORG_ADMIN}-only (enforced by {@code @PreAuthorize} in the controller). */
    @Transactional
    public UserSummaryResponse create(CreateUserRequest request, AuthenticatedPrincipal caller) {
        if (request.role() == Role.SUPER_ADMIN) {
            // Not a Bean Validation constraint because it's a rule about
            // this endpoint specifically, not a property of the Role enum
            // in isolation - SUPER_ADMIN is a legitimate value elsewhere.
            throw new IllegalArgumentException("Cannot create a SUPER_ADMIN account through organization user management");
        }
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
}
