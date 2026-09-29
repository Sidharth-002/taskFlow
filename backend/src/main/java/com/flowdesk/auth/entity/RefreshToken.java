package com.flowdesk.auth.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import com.flowdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single issued refresh token.
 *
 * <p><b>Why a hash, not the raw token:</b> the same reasoning as password
 * storage - if this table leaked, the raw tokens (each one a bearer
 * credential good for a new access token) must not be directly usable.
 * Unlike passwords, this uses SHA-256 rather than BCrypt: refresh tokens
 * are looked up by exact match on every refresh/logout call, which
 * requires a deterministic hash, whereas BCrypt salts each hash
 * differently by design and is meant to be slow. That's safe specifically
 * because the raw token is a cryptographically random, high-entropy value
 * (see {@code AuthService.generateRawRefreshToken}) - not a low-entropy
 * secret a human chose, which is what BCrypt's slowness defends against.
 *
 * <p><b>Rotation:</b> each successful {@code /api/auth/refresh} call
 * revokes the presented token and issues a brand new row, rather than
 * reusing/extending the same one. A revoked token being presented again
 * (e.g. a stolen refresh token used after the legitimate client already
 * rotated it) is rejected outright.
 *
 * <p><b>{@code @Version}:</b> without it, two concurrent {@code /refresh}
 * calls presenting the same token could both read {@code revoked = false}
 * before either transaction commits, and both would then mint a valid new
 * session from what should only be a single-use token. Optimistic locking
 * makes the second writer's commit fail with
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException},
 * which {@code AuthService.refresh} treats the same as an already-revoked
 * token.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
