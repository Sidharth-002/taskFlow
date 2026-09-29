package com.flowdesk.auth.repository;

import com.flowdesk.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * A bulk update rather than loading every token and revoking each one
     * in Java - a user could plausibly have several active sessions
     * (multiple devices/tabs), and this is a single UPDATE statement
     * regardless of how many. Used when deactivating a user or changing
     * their role (see {@code AuthService.revokeAllTokensForUser}), so
     * their access ends promptly rather than only once each outstanding
     * refresh token individually expires.
     *
     * <p>{@code clearAutomatically = true} matters here, not just as a
     * style preference: a bulk JPQL update executes directly against the
     * database, bypassing the persistence context entirely, so any
     * {@code RefreshToken} already loaded into the current session keeps
     * its stale in-memory {@code revoked = false} even after this method
     * returns - Hibernate's first-level cache doesn't know the row
     * changed underneath it, and a plain query by a non-ID field like
     * {@code findByTokenHash} would return that stale managed instance
     * rather than the fresh row. Found via
     * {@code UserManagementIT.changingRole_revokesExistingRefreshToken}:
     * without {@code clearAutomatically}, a refresh attempt with the
     * old token right after a role change wrongly succeeded (200
     * instead of 401), because the token object loaded earlier in the
     * same test's transaction was still cached with {@code revoked = false}.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllActiveTokensForUser(@Param("userId") Long userId);

    /**
     * Backs {@code RefreshTokenCleanupJob} - without this, the table only
     * ever grows: rotation (Phase 3) revokes a token but never deletes the
     * row, and {@code revokeAllActiveTokensForUser} (Phase 5) does the
     * same in bulk. Deletes purely by {@code expiresAt}, not
     * {@code revoked} - a revoked-but-not-yet-expired token (the normal
     * case, immediately after rotation) is deliberately left alone; it's
     * harmless dead weight until its natural expiry passes, at which point
     * a later run of this job removes it. This also means the job never
     * needs a separate "revoked long enough ago" cutoff, just one based on
     * a column every row already has.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
