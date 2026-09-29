package com.flowdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Same rationale as {@link RestAuthenticationEntryPoint}: an authenticated
 * but unauthorized request can be rejected by the filter chain's
 * {@code authorizeHttpRequests} rules before reaching a controller, so it
 * needs its own JSON writer rather than relying on
 * {@code @RestControllerAdvice}. {@code @PreAuthorize} failures inside a
 * controller method, by contrast, do reach {@code GlobalExceptionHandler}
 * normally.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED",
                "You do not have permission to perform this action",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
