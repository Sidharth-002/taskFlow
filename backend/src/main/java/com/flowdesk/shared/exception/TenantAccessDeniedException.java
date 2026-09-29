package com.flowdesk.shared.exception;

/**
 * The caller is authenticated and the resource exists, but it belongs to a
 * different organization than the caller's.
 *
 * <p>Kept as a distinct type from {@link ResourceNotFoundException} for
 * internal clarity (logs and code make it obvious <em>why</em> access was
 * denied), but {@link GlobalExceptionHandler} deliberately maps it to the
 * same 404 response as a genuinely missing resource. Returning a
 * different status (e.g. 403) would confirm to the caller that a resource
 * with that ID exists in <em>some</em> other organization - a minor but
 * real information leak this project avoids by design (see the
 * multi-tenancy section of the README).
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
