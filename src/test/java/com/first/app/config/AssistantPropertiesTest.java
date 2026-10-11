package com.first.app.config;

import com.first.app.integration.AssistantTools;
import com.first.app.integration.TravelMcpTools;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AssistantConfig.class)
            .withBean(ChatModel.class, () -> Mockito.mock(ChatModel.class))
            .withBean(AssistantTools.class, () -> new AssistantTools(Mockito.mock(TravelMcpTools.class)));

    @Test
    void bindsAssistantPropertiesFromTheAppAssistantPrefix() {
        runner.withPropertyValues(
                        "app.assistant.api-key=test-key",
                        "app.assistant.history-ttl=24h",
                        "app.assistant.window-messages=20",
                        "app.assistant.window-chars=6000",
                        "app.assistant.max-message-length=2000",
                        "app.assistant.rate-limit-per-minute=10")
                .run(context -> {
                    AssistantProperties properties = context.getBean(AssistantProperties.class);
                    assertThat(properties.apiKey()).isEqualTo("test-key");
                    assertThat(properties.historyTtl()).isEqualTo(Duration.ofHours(24));
                    assertThat(properties.windowMessages()).isEqualTo(20);
                    assertThat(properties.windowChars()).isEqualTo(6000);
                    assertThat(properties.maxMessageLength()).isEqualTo(2000);
                    assertThat(properties.rateLimitPerMinute()).isEqualTo(10);
                    assertThat(properties.configured()).isTrue();
                });
    }

    @Test
    void missingKeys_stillResolveToTheBoundedDefaults() {
        runner.run(context -> {
            AssistantProperties properties = context.getBean(AssistantProperties.class);
            assertThat(properties.historyTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.windowMessages()).isEqualTo(20);
            assertThat(properties.windowChars()).isEqualTo(6000);
            assertThat(properties.maxMessageLength()).isEqualTo(2000);
            assertThat(properties.rateLimitPerMinute()).isEqualTo(10);
            assertThat(properties.configured()).isFalse();
        });
    }

    @Test
    void blankApiKey_reportsNotConfigured() {
        runner.withPropertyValues("app.assistant.api-key=")
                .run(context -> assertThat(context.getBean(AssistantProperties.class).configured()).isFalse());
    }
}
