package com.first.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Knowledge-base configuration bound from {@code app.knowledge-base.*} in the active profile.
 *
 * <p>{@code embeddingApiKey} mirrors the {@code ZHIPU_API_KEY} environment variable (empty when
 * unset) and is the single gate: {@link #configured()} false keeps indexing a no-op and the
 * assistant's search tool answers "unavailable" without any provider or vector-store call. All
 * bounds resolve to the design defaults even when a profile omits them.
 */
@ConfigurationProperties(prefix = "app.knowledge-base")
public record KnowledgeBaseProperties(
        String embeddingApiKey,
        String embeddingBaseUrl,
        String embeddingModel,
        int embeddingDimensions,
        int topK,
        int chunkSizeChars,
        int chunkOverlapChars,
        String chromaUrl,
        String collectionName) {

    public KnowledgeBaseProperties {
        if (!StringUtils.hasText(embeddingBaseUrl)) {
            embeddingBaseUrl = "https://open.bigmodel.cn/api/paas/v4";
        }
        if (!StringUtils.hasText(embeddingModel)) {
            embeddingModel = "embedding-3";
        }
        if (embeddingDimensions <= 0) {
            embeddingDimensions = 2048;
        }
        if (topK <= 0) {
            topK = 4;
        }
        if (chunkSizeChars <= 0) {
            chunkSizeChars = 800;
        }
        if (chunkOverlapChars <= 0) {
            chunkOverlapChars = 100;
        }
        if (!StringUtils.hasText(chromaUrl)) {
            chromaUrl = "http://localhost:8000";
        }
        if (!StringUtils.hasText(collectionName)) {
            collectionName = "wanderchina_knowledge";
        }
    }

    /** True when a real embedding key is present; blank keeps indexing and search inert. */
    public boolean configured() {
        return StringUtils.hasText(embeddingApiKey);
    }
}
