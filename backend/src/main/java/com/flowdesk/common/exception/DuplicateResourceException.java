package com.flowdesk.common.exception;

/** A create operation conflicts with an existing unique resource (e.g. email already registered). */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
