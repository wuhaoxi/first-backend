package com.first.app.config;

import com.first.app.integration.ChromaKnowledgeVectorStore;
import com.first.app.integration.KnowledgeVectorStore;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knowledge-base wiring. Deliberately manual (the Chroma <em>library</em>, no starter) so
 * application startup never contacts the vector store; beans land here as this change's
 * phases require them.
 *
 * <p>The embedding model is assembled against Zhipu's OpenAI-compatible endpoint instead of
 * relying on the autoconfigured OpenAI embedding bean (which points at DeepSeek, the chat
 * provider). A placeholder key keeps the bean buildable while
 * {@link KnowledgeBaseProperties#configured()} gates every real call.
 */
@Configuration
@EnableConfigurationProperties(KnowledgeBaseProperties.class)
public class KnowledgeBaseConfig {

    /** Mirrors the placeholder pattern of {@code spring.ai.openai.api-key} in application.yml. */
    static final String PLACEHOLDER_API_KEY = "sk-not-configured";

    /** Zhipu serves embeddings at {@code {base-url}/embeddings}, not the OpenAI {@code /v1/embeddings}. */
    static final String ZHIPU_EMBEDDINGS_PATH = "/embeddings";

    @Bean
    EmbeddingModel zhipuEmbeddingModel(KnowledgeBaseProperties properties) {
        return buildEmbeddingModel(properties);
    }

    /**
     * Store bean construction never contacts Chroma — the adapter connects lazily on first use.
     * The embedding model is qualified to the Zhipu bean; the autoconfigured OpenAI embedding
     * model points at DeepSeek and must never reach the vector store.
     */
    @Bean
    KnowledgeVectorStore knowledgeVectorStore(@Qualifier("zhipuEmbeddingModel") EmbeddingModel embeddingModel,
            KnowledgeBaseProperties properties) {
        ChromaApi chromaApi = ChromaApi.builder().baseUrl(properties.chromaUrl()).build();
        return new ChromaKnowledgeVectorStore(chromaApi, embeddingModel, properties.collectionName());
    }

    static EmbeddingModel buildEmbeddingModel(KnowledgeBaseProperties properties) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.embeddingBaseUrl())
                .embeddingsPath(ZHIPU_EMBEDDINGS_PATH)
                .apiKey(properties.configured() ? properties.embeddingApiKey() : PLACEHOLDER_API_KEY)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.embeddingModel())
                .dimensions(properties.embeddingDimensions())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
