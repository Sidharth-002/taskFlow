package com.flowdesk.common.exception;

import com.flowdesk.auth.exception.InvalidRefreshTokenException;
import com.flowdesk.ticket.exception.InvalidTicketTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the single {@link ErrorResponse} shape used
 * across every FlowDesk API (Section 23 of the spec).
 *
 * <p>This only covers exceptions thrown during controller/service
 * execution, i.e. inside the {@code DispatcherServlet}. Authentication and
 * authorization failures raised earlier, in the Spring Security filter
 * chain (a missing/invalid JWT, or {@code anyRequest().authenticated()}
 * rejecting an unauthenticated request), never reach this class - those
 * are handled by {@link com.flowdesk.security.RestAuthenticationEntryPoint}
 * and {@link com.flowdesk.security.RestAccessDeniedHandler}, which produce
 * the same {@link ErrorResponse} shape from outside the dispatcher.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    /**
     * Mapped to the same 404 shape as a genuinely missing resource - see
     * {@link TenantAccessDeniedException}'s Javadoc for why.
     */
    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleTenantAccessDenied(
            TenantAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Tenant access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(
            UnauthorizedOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTicketTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidTicketTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "INVALID_TICKET_TRANSITION", ex.getMessage(), request);
    }

    /**
     * A concurrent edit won the race (see {@code Ticket.version}). Distinct
     * {@code code} from {@link InvalidTicketTransitionException} even
     * though both are 409s, so the client can tell "your requested change
     * doesn't make sense" apart from "someone else changed this first - go
     * reload and retry".
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "This resource was modified by someone else - please reload and try again",
                request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", ex.getMessage(), request);
    }

    /**
     * Covers both wrong-password and unknown-email cases (Spring Security's
     * {@code DaoAuthenticationProvider} throws {@code BadCredentialsException}
     * for both, and {@code CustomUserDetailsService} deliberately does the
     * same rather than a distinguishable "user not found" - revealing which
     * one failed would let an attacker enumerate registered email addresses).
     */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(Exception ex, HttpServletRequest request) {
        log.warn("Authentication failed for request to {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    /**
     * A request-shape problem that isn't expressible as a Bean Validation
     * constraint on the DTO itself (e.g. "role must not be SUPER_ADMIN
     * here" in {@code UserService.create} - a business rule about the
     * combination of endpoint and value, not a property of the field in
     * isolation).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ErrorResponse body = ErrorResponse.validation(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "One or more fields are invalid",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request to {}", request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
