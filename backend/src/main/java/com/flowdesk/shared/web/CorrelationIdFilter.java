package com.flowdesk.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@code correlationId} MDC key referenced by
 * {@code application.yml}'s {@code logging.pattern.level} - every log line
 * written while handling a request carries the same ID, so grepping one
 * request's logs out of a busy server (or, in production, out of a log
 * aggregator) is a single string match instead of reconstructing the
 * sequence from timestamps.
 *
 * <p>Reuses an incoming {@code X-Correlation-Id} header if the caller (or
 * an upstream reverse proxy/gateway) already supplied one, generating a
 * fresh UUID otherwise - this lets a request be traced end-to-end across
 * multiple services in a larger deployment, not just within this one. The
 * ID is echoed back on the response either way, so a client that didn't
 * send one can still learn it after the fact (e.g. to reference in a bug
 * report or support request).
 *
 * <p>Registered as a plain {@code @Component} with
 * {@code @Order(HIGHEST_PRECEDENCE)}, not via
 * {@code SecurityConfig.addFilterBefore} like {@code JwtAuthenticationFilter}/
 * {@code AuthRateLimitFilter} - those calls only control ordering *within*
 * Spring Security's own filter chain, which is itself just one filter in
 * the servlet container's pipeline. This needs to run before that entire
 * chain, so that even a request rejected by rate limiting or
 * authentication (never reaching a controller) still gets logged with a
 * correlation ID and still gets the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always cleared, even on an exception - MDC is thread-local
            // and this thread will be reused for a later, unrelated
            // request from the servlet container's thread pool. Leaving a
            // stale value would mislabel that request's log lines.
            MDC.remove(MDC_KEY);
        }
    }
}
