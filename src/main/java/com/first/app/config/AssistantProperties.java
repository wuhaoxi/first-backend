package com.first.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Assistant configuration bound from {@code app.assistant.*} in the active profile.
 *
 * <p>{@code api-key} mirrors the {@code DEEPSEEK_API_KEY} environment variable (empty when
 * unset) and is the single gate: {@link #configured()} false keeps the chat endpoint at 503
 * without any provider call ever leaving the JVM. The history window and rate limits are
 * bounded even when a profile omits them.
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        String apiKey,
        Duration historyTtl,
        int windowMessages,
        int windowChars,
        int maxMessageLength,
        int rateLimitPerMinute) {

    public AssistantProperties {
        if (historyTtl == null) {
            historyTtl = Duration.ofHours(24);
        }
        if (windowMessages <= 0) {
            windowMessages = 20;
        }
        if (windowChars <= 0) {
            windowChars = 6000;
        }
        if (maxMessageLength <= 0) {
            maxMessageLength = 2000;
        }
        if (rateLimitPerMinute <= 0) {
            rateLimitPerMinute = 10;
        }
    }

    /** True when a real provider key is present; blank keeps the endpoint at 503. */
    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }
}
