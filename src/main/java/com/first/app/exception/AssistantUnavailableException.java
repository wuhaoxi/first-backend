package com.first.app.exception;

/** The assistant has no provider key configured; rendered as 503 by {@code GlobalExceptionHandler}. */
public class AssistantUnavailableException extends RuntimeException {
    public AssistantUnavailableException(String message) {
        super(message);
    }
}
