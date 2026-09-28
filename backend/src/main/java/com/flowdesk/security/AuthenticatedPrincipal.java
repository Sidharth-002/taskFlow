package com.flowdesk.security;

import com.flowdesk.user.entity.Role;

/**
 * The authenticated identity attached to the {@code SecurityContext} for
 * every request carrying a valid JWT. Built directly from the token's
 * claims by {@link JwtAuthenticationFilter} - deliberately <b>not</b>
 * loaded from the database on each request, per the spec's explicit
 * guidance against an expensive per-request lookup to authenticate access
 * tokens.
 *
 * <p>Trade-off this implies: if a user is deactivated or their role
 * changes, an already-issued access token remains valid (as this class,
 * and thus the caller's permissions, is reconstructed purely from the
 * token) until it naturally expires - at most {@code app.jwt.access-token-ttl}
 * later. Obtaining a <em>new</em> access token via refresh does hit the
 * database and re-checks {@code User.active}, so this staleness window is
 * bounded and does not survive a refresh.
 *
 * @param organizationId {@code null} for {@link Role#SUPER_ADMIN}, matching {@link com.flowdesk.user.entity.User}.
 */
public record AuthenticatedPrincipal(Long userId, Long organizationId, String email, Role role) {
}
