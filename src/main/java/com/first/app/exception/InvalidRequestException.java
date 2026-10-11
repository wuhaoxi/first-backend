package com.first.app.exception;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A client-input rejection rendered as 400 by {@link GlobalExceptionHandler}. When the violation
 * is tied to request fields, the optional field names are surfaced as field-level
 * {@code errors[]} entries ({@code {field, message}} per entry); exceptions without fields keep
 * the message-only contract.
 */
public class InvalidRequestException extends RuntimeException {

    private final List<Map<String, String>> errors;

    public InvalidRequestException(String message) {
        super(message);
        this.errors = List.of();
    }

    public InvalidRequestException(String message, String... fields) {
        super(message);
        this.errors = Arrays.stream(fields)
                .map(field -> Map.of("field", field, "message", message))
                .toList();
    }

    /** Field-level error entries for the standard {@code ErrorResponse.errors[]} array. */
    public List<Map<String, String>> getErrors() {
        return errors;
    }
}
