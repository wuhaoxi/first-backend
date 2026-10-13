package com.first.app.dto;

import java.util.List;

/** Response body of {@code POST /api/assistant/post-assist/tags}: 1–8 sanitized tags. */
public record TagSuggestionsResponse(List<String> tags) {
}
