package com.flowdesk.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.shared.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies {@link RateLimiterService} to the three auth endpoints that are
 * {@code permitAll()} in {@code SecurityConfig} and therefore have no
 * other per-request cost to an attacker - {@code register}/{@code login}/
 * {@code refresh}. Registered via {@code SecurityConfig.addFilterBefore},
 * ahead of {@code JwtAuthenticationFilter}, so a request that's going to
 * be rejected here never even reaches JWT parsing or (for {@code login})
 * the database-backed {@code AuthenticationManager}.
 *
 * <p>Every other endpoint passes through untouched - {@code logout} isn't
 * limited (revoking a token you already hold isn't a useful attack
 * surface), and every authenticated endpoint already requires a valid JWT,
 * which is a much stronger per-request cost than an IP-based counter
 * would add.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimiterService rateLimiterService, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = bucketFor(request);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = "ratelimit:%s:%s".formatted(bucket.name(), clientIp(request));
        if (rateLimiterService.tryConsume(key, bucket.config().capacity(), bucket.config().window())) {
            filterChain.doFilter(request, response);
            return;
        }

        respondTooManyRequests(response, request, bucket.config().window());
    }

    private record Bucket(String name, RateLimitProperties.Bucket config) {
    }

    private Bucket bucketFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case "/api/auth/register" -> new Bucket("register", properties.register());
            case "/api/auth/login" -> new Bucket("login", properties.login());
            case "/api/auth/refresh" -> new Bucket("refresh", properties.refresh());
            default -> null;
        };
    }

    /**
     * Deliberately {@code request.getRemoteAddr()}, never a client-supplied
     * header like {@code X-Forwarded-For}: trusting a header the caller
     * controls would let anyone bypass the limit by sending a different
     * value on every request. If FlowDesk is ever deployed behind a
     * reverse proxy that terminates client connections, that proxy - and
     * only that proxy, configured as a trusted source - should be the one
     * populating the real client IP for this filter to read.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response, HttpServletRequest request, Duration window)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Too many requests - please try again later",
                request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
