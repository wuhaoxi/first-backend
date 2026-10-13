package com.first.app.dto;

/**
 * SSE payloads of the post-polish stream. Each frame carries exactly one of these:
 * zero or more {@link Delta} (concatenate in arrival order), then exactly one terminal
 * {@link Done} on success or {@link Error} on failure. Separate from the chat
 * {@code AssistantEvent} because polish has no conversation identity.
 */
public sealed interface PostPolishEvent {

    /** Incremental polished text. */
    record Delta(String text) implements PostPolishEvent {
    }

    /** Terminal success. */
    record Done() implements PostPolishEvent {
    }

    /** Terminal failure carrying a sanitized, user-facing message (never upstream details). */
    record Error(String message) implements PostPolishEvent {
    }
}
