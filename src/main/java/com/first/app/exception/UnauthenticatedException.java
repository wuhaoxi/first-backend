package com.first.app.exception;

/**
 * A protected endpoint was reached without an authenticated {@code userId} request attribute;
 * rendered as 401 by {@code GlobalExceptionHandler}. In production the security filter chain
 * rejects such requests first — this is the defense-in-depth path for async endpoints.
 */
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException(String message) {
        super(message);
    }
}
