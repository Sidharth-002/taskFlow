package com.flowdesk.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.jwt.*} configuration. Registered via
 * {@code @ConfigurationPropertiesScan} on {@link com.flowdesk.FlowDeskApplication}.
 *
 * @param secret HMAC-SHA signing key. Must be at least 256 bits (32
 *               characters) for HS256 - {@link JwtService} fails fast at
 *               startup otherwise rather than producing tokens with a
 *               weak key. No default in the prod profile: a missing value
 *               fails application startup rather than falling back to a
 *               guessable secret.
 * @param accessTokenTtl short-lived; validated on every request without a
 *                       database lookup (see {@link JwtAuthenticationFilter}).
 * @param refreshTokenTtl how long an issued refresh token remains usable
 *                        before it must be exchanged for a new session
 *                        entirely (i.e. the user has to log in again).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
