package com.flowdesk.auth.exception;

/**
 * The presented refresh token is missing, expired, already revoked (e.g.
 * reused after rotation, or after logout), or doesn't belong to an active
 * user. Deliberately doesn't distinguish which of those applies in the
 * response - that detail is only useful to an attacker.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
