package com.first.app.dto;

/**
 * Request body of {@code POST /api/assistant/chat}. A blank {@code conversationId} means
 * "new conversation" — the server generates a UUID and echoes it in the terminal {@code done}.
 */
public record AssistantChatRequest(String message, String conversationId) {
}
