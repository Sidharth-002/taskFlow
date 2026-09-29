package com.flowdesk.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single error response shape returned by every FlowDesk API error,
 * per the spec's Section 23 contract. {@code fieldErrors} is only present
 * for bean-validation failures (Jackson's {@code non_null} inclusion,
 * configured globally in {@code application.yml}, drops it otherwise).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ErrorResponse validation(
            int status, String code, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }
}
