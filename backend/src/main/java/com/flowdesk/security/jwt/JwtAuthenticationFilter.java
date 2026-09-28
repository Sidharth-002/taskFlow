package com.flowdesk.security.jwt;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.security.handler.RestAuthenticationEntryPoint;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates each request from its {@code Authorization: Bearer <token>}
 * header by validating the JWT's signature and expiry - no database lookup
 * (see {@link AuthenticatedPrincipal} for the trade-off this implies).
 *
 * <p>A missing, malformed, or expired token is not treated as an error
 * here: the filter simply leaves the request unauthenticated and lets it
 * continue down the chain. Whether that matters is decided afterwards by
 * {@code SecurityConfig}'s {@code authorizeHttpRequests} rules - a public
 * endpoint proceeds fine with no principal, while a protected one is
 * rejected by {@link RestAuthenticationEntryPoint} with a 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthenticatedPrincipal principal = jwtService.parseAndValidate(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Malformed, expired, or signed with a different key.
                // Never logged at a level that would flood logs with
                // attacker-controlled noise; the token itself is never logged.
                log.debug("Rejected invalid access token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
