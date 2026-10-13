package com.first.app.dto;

/**
 * Request body of the {@code POST /api/assistant/post-assist/*} endpoints. {@code content} is
 * the post body under assistance (required); {@code title} is the current title draft, when one
 * exists. Both are validated by the service before any model or rate-limit interaction.
 */
public record PostAssistRequest(String title, String content) {
}
