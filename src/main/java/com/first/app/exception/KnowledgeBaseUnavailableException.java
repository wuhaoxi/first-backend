package com.first.app.exception;

/** The vector store (Chroma) could not be reached or answered unexpectedly; rendered as 503 by {@code GlobalExceptionHandler}. */
public class KnowledgeBaseUnavailableException extends RuntimeException {

    public KnowledgeBaseUnavailableException(String message) {
        super(message);
    }

    public KnowledgeBaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
