package com.first.app.exception;

/** Per-user assistant rate limit exceeded; rendered as 429 by {@code GlobalExceptionHandler}. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
