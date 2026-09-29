package com.flowdesk.common.exception;

/**
 * The caller is authenticated, and would generally be allowed to call this
 * endpoint (it already passed any {@code @PreAuthorize} role check), but
 * this specific operation on this specific resource isn't theirs to
 * perform - e.g. an {@code AGENT} trying to reassign a ticket to someone
 * else (only {@code ORG_ADMIN}/{@code TEAM_LEAD} may), or a user editing
 * another user's comment.
 *
 * <p>This is deliberately a separate concern from Spring Security's
 * {@code AccessDeniedException}: that one is "you don't have this role at
 * all", raised by {@code @PreAuthorize} before the method body runs. This
 * one is "you have the right role, but not over this particular row",
 * which can only be determined once the resource has been loaded.
 */
public class UnauthorizedOperationException extends RuntimeException {

    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
