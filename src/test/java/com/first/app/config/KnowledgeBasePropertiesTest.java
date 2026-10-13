package com.first.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBasePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KnowledgeBaseConfig.class);

    @Test
    void bindsKnowledgeBasePropertiesFromTheAppKnowledgeBasePrefix() {
        runner.withPropertyValues(
                        "app.knowledge-base.embedding-api-key=test-key",
                        "app.knowledge-base.embedding-base-url=https://example.test/v4",
                        "app.knowledge-base.embedding-model=custom-model",
                        "app.knowledge-base.embedding-dimensions=512",
                        "app.knowledge-base.top-k=7",
                        "app.knowledge-base.chunk-size-chars=500",
                        "app.knowledge-base.chunk-overlap-chars=50",
                        "app.knowledge-base.chroma-url=http://example.test:9000",
                        "app.knowledge-base.collection-name=custom_collection")
                .run(context -> {
                    KnowledgeBaseProperties properties = context.getBean(KnowledgeBaseProperties.class);
                    assertThat(properties.embeddingApiKey()).isEqualTo("test-key");
                    assertThat(properties.embeddingBaseUrl()).isEqualTo("https://example.test/v4");
                    assertThat(properties.embeddingModel()).isEqualTo("custom-model");
                    assertThat(properties.embeddingDimensions()).isEqualTo(512);
                    assertThat(properties.topK()).isEqualTo(7);
                    assertThat(properties.chunkSizeChars()).isEqualTo(500);
                    assertThat(properties.chunkOverlapChars()).isEqualTo(50);
                    assertThat(properties.chromaUrl()).isEqualTo("http://example.test:9000");
                    assertThat(properties.collectionName()).isEqualTo("custom_collection");
                    assertThat(properties.configured()).isTrue();
                });
    }

    @Test
    void missingKeys_stillResolveToTheBoundedDefaults() {
        runner.run(context -> {
            KnowledgeBaseProperties properties = context.getBean(KnowledgeBaseProperties.class);
            assertThat(properties.embeddingBaseUrl()).isEqualTo("https://open.bigmodel.cn/api/paas/v4");
            assertThat(properties.embeddingModel()).isEqualTo("embedding-3");
            assertThat(properties.embeddingDimensions()).isEqualTo(2048);
            assertThat(properties.topK()).isEqualTo(4);
            assertThat(properties.chunkSizeChars()).isEqualTo(800);
            assertThat(properties.chunkOverlapChars()).isEqualTo(100);
            assertThat(properties.chromaUrl()).isEqualTo("http://localhost:8000");
            assertThat(properties.collectionName()).isEqualTo("wanderchina_knowledge");
            assertThat(properties.configured()).isFalse();
        });
    }

    @Test
    void blankApiKey_reportsNotConfigured() {
        runner.withPropertyValues("app.knowledge-base.embedding-api-key=")
                .run(context -> assertThat(context.getBean(KnowledgeBaseProperties.class).configured()).isFalse());
    }
}
