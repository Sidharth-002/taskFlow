package com.flowdesk.common.exception;

/** A requested entity does not exist (or is not visible to the caller). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException(resourceName + " " + id + " does not exist");
    }
}
