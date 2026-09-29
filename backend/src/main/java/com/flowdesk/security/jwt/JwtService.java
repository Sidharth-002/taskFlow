package com.flowdesk.security;

import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates FlowDesk's self-signed JWT access tokens (HS256).
 *
 * <p>Refresh tokens are handled separately (see
 * {@code auth.service.AuthService}) - they're opaque random strings
 * persisted in {@code refresh_tokens}, not JWTs, since they're checked
 * against the database anyway (for rotation/revocation) so there's no
 * benefit to making them self-describing tokens.
 */
@Service
public class JwtService {

    private static final String CLAIM_ORGANIZATION_ID = "organizationId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 requires a >= 256-bit key; fail fast at startup rather
            // than let Keys.hmacShaKeyFor throw deep inside a request.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());

        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (user.getOrganization() != null) {
            builder.claim(CLAIM_ORGANIZATION_ID, user.getOrganization().getId());
        }

        return builder.signWith(signingKey).compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /**
     * @throws JwtException if the token is malformed, expired, or its
     *                       signature doesn't match - callers (only
     *                       {@link JwtAuthenticationFilter}) treat any
     *                       subtype the same way: the request simply isn't
     *                       authenticated.
     */
    public AuthenticatedPrincipal parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long organizationId = claims.get(CLAIM_ORGANIZATION_ID, Long.class);
        return new AuthenticatedPrincipal(
                Long.valueOf(claims.getSubject()),
                organizationId,
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
