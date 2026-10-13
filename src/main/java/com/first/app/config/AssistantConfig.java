package com.first.app.config;

import com.first.app.integration.AssistantTools;
import com.first.app.integration.AttractionTools;
import com.first.app.integration.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registers the assistant configuration and the two {@code ChatClient} beans (built once, reused
 * per request): the tool-enabled chat client ({@code @Primary}, injected unqualified by
 * {@code AssistantService}) with the weather/rate/knowledge/attraction tools attached by
 * default, and the tools-less post-assist client ({@code postAssistChatClient}).
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    @Bean
    @Primary
    ChatClient assistantChatClient(ChatModel chatModel, AssistantTools assistantTools,
            KnowledgeTools knowledgeTools, AttractionTools attractionTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(assistantTools, knowledgeTools, attractionTools)
                .build();
    }

    /** Tools-less client for the post-assist endpoints — tool definitions would waste tokens. */
    @Bean
    ChatClient postAssistChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
