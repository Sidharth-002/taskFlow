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
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

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
            throw new IllegalArgumentException("SUPER_ADMIN cannot be assigned through organization user management");
        }
    }

    private User requireInOrganization(Long id, Long organizationId) {
        return userRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
