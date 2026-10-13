package com.first.app.dto;

import java.util.List;

/** Response body of {@code POST /api/assistant/post-assist/title}: 1–3 sanitized candidates. */
public record TitleSuggestionsResponse(List<String> suggestions) {
}
