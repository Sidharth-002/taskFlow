package com.flowdesk.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Produces the same {@link ErrorResponse} JSON shape as
 * {@link com.flowdesk.shared.exception.GlobalExceptionHandler} for requests
 * rejected by the Spring Security filter chain itself - a missing or
 * invalid JWT on a protected endpoint - which happens before the request
 * ever reaches a controller, so {@code @RestControllerAdvice} cannot
 * intercept it.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHENTICATED",
                "Authentication is required to access this resource",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
