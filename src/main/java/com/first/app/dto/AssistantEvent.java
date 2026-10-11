package com.first.app.dto;

/**
 * SSE payloads of the assistant chat stream. Each frame carries exactly one of these:
 * zero or more {@link Delta} (concatenate in arrival order), then exactly one terminal
 * {@link Done} on success or {@link Error} on failure.
 */
public sealed interface AssistantEvent {

    /** Incremental assistant text. */
    record Delta(String text) implements AssistantEvent {
    }

    /** Terminal success: the conversation id to reuse for the next turn. */
    record Done(String conversationId) implements AssistantEvent {
    }

    /** Terminal failure carrying a sanitized, user-facing message (never upstream details). */
    record Error(String message) implements AssistantEvent {
    }
}
