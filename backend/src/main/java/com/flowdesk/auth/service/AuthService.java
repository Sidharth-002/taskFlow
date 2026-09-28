package com.flowdesk.auth.service;

import com.flowdesk.auth.dto.AuthResponse;
import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.RefreshRequest;
import com.flowdesk.auth.dto.RegisterRequest;
import com.flowdesk.auth.entity.RefreshToken;
import com.flowdesk.auth.exception.InvalidRefreshTokenException;
import com.flowdesk.auth.repository.RefreshTokenRepository;
import com.flowdesk.shared.exception.DuplicateResourceException;
import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.security.jwt.JwtProperties;
import com.flowdesk.security.jwt.JwtService;
import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.mapper.UserMapper;
import com.flowdesk.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_TOKEN_BYTES = 32; // 256 bits of entropy

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserMapper userMapper) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
    }

    /**
     * Provisions a brand-new organization with the caller as its first
     * {@code ORG_ADMIN}, then logs them in immediately (returns tokens
     * directly rather than requiring a separate login call).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            // Deliberately vague to the caller (avoids confirming an email
            // is registered under a *different* flow than login does), but
            // specific enough server-side for debugging via logs.
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Organization organization = organizationRepository.save(
                Organization.builder().name(request.organizationName()).build());

        User user = userRepository.save(User.builder()
                .organization(organization)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.ORG_ADMIN)
                .build());

        log.info("Registered new organization {} with admin user {}", organization.getId(), user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Delegates to DaoAuthenticationProvider -> CustomUserDetailsService
        // -> PasswordEncoder. Throws BadCredentialsException (wrong
        // password or unknown email) or DisabledException (deactivated
        // user) on failure - both translated to a uniform 401 by
        // GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException(
                        "Authentication succeeded but user %s could not be reloaded".formatted(request.email())));

        log.info("User {} logged in", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Rotates the refresh token: the presented one is revoked and a new
     * access/refresh pair is issued. A token that's missing, expired, or
     * already revoked (including one already used once before) is
     * rejected uniformly - as is one belonging to a now-deactivated user.
     *
     * <p><b>Found during Phase 5 hardening:</b> this check was missing
     * entirely until now - the README claimed refresh "re-checks
     * {@code User.active}" (written as the intended design in Phase 3),
     * but the code never actually did it, so a deactivated user could
     * keep refreshing their session for up to {@code app.jwt.refresh-token-ttl}
     * (7 days by default) after being deactivated. See
     * {@code UserService.setActive}, which now also proactively revokes
     * every outstanding refresh token the moment a user is deactivated,
     * rather than relying solely on this check rejecting the next refresh
     * attempt.
     *
     * <p>The revoke is flushed immediately (rather than left for
     * end-of-transaction commit) specifically so a concurrent rotation of
     * the same token - caught via {@code RefreshToken}'s {@code @Version} -
     * surfaces here as a normal, catchable failure instead of only at
     * commit time, where it would be too late to translate into a clean
     * {@link InvalidRefreshTokenException}.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid or expired"));

        if (!token.getUser().isActive()) {
            throw new InvalidRefreshTokenException("Refresh token is invalid or expired");
        }

        token.setRevoked(true);
        try {
            refreshTokenRepository.saveAndFlush(token);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Another request rotated this exact token first - treat it
            // exactly like an already-revoked token.
            throw new InvalidRefreshTokenException("Refresh token is invalid or expired");
        }

        log.info("Rotated refresh token for user {}", token.getUser().getId());
        return buildAuthResponse(token.getUser());
    }

    /**
     * Idempotent by design: revokes the token if it's found and still
     * usable, otherwise does nothing. Logout returning an error for an
     * already-expired/invalid token would only give an attacker a way to
     * probe token validity.
     */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("User {} logged out", token.getUser().getId());
                });
    }

    /**
     * Backs {@code GET /api/auth/me}. Unlike request authentication itself
     * (see {@link com.flowdesk.security.jwt.JwtAuthenticationFilter}), this
     * intentionally does read the database: the JWT only carries
     * {@code userId}/{@code organizationId}/{@code email}/{@code role},
     * not the full profile (first/last name) a "who am I" endpoint needs
     * to return. This is a single read for display purposes, not a
     * re-validation of the token's authenticity.
     */
    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        return userMapper.toSummary(user);
    }

    /**
     * Revokes every outstanding refresh token for a user, so their access
     * ends as soon as their current (short-lived) access token expires,
     * instead of them being able to keep refreshing into new sessions.
     * Called by {@code UserService} when a user is deactivated or has
     * their role changed - both are permission-affecting changes that
     * should end existing sessions rather than let them ride out on
     * stale claims.
     */
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        int revoked = refreshTokenRepository.revokeAllActiveTokensForUser(userId);
        if (revoked > 0) {
            log.info("Revoked {} active refresh token(s) for user {}", revoked, userId);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user);
        return AuthResponse.of(accessToken, refreshToken, jwtService.accessTokenTtlSeconds(), userMapper.toSummary(user));
    }

    private String issueRefreshToken(User user) {
        String rawToken = generateRawRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
