package com.first.app.config;

import com.first.app.integration.AssistantTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the assistant configuration and the single {@code ChatClient} (built once, reused per
 * request) with the two weather/rate tools attached by default.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    @Bean
    ChatClient assistantChatClient(ChatModel chatModel, AssistantTools assistantTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(assistantTools)
                .build();
    }
}
