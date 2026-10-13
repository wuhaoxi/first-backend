package com.first.app.integration;

import com.first.app.exception.KnowledgeBaseUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.testcontainers.chromadb.ChromaDBContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromaKnowledgeVectorStoreTest {

    private static final String CHROMA_IMAGE = "ghcr.io/chroma-core/chroma:1.0.15";

    private static final ChromaDBContainer CHROMA = new ChromaDBContainer(CHROMA_IMAGE);

    private static KnowledgeVectorStore store;

    @BeforeAll
    static void setUp() {
        CHROMA.start();
        store = new ChromaKnowledgeVectorStore(
                ChromaApi.builder().baseUrl(CHROMA.getEndpoint()).build(),
                deterministicEmbeddingModel(),
                "wanderchina_knowledge_test");
    }

    @AfterAll
    static void tearDown() {
        CHROMA.stop();
    }

    @Test
    void addThenSearch_returnsTheChunkWithItsMetadata() {
        store.add(List.of(chunk("posts:901:0", "西湖", "西湖四季皆宜，春天最美。", "posts", 901, 0)));

        List<KnowledgeVectorStore.Hit> hits = store.search("西湖值得去吗", 4);

        assertThat(hits).isNotEmpty();
        assertThat(hits).anySatisfy(hit -> {
            assertThat(hit.text()).contains("西湖四季皆宜");
            assertThat(hit.metadata()).containsEntry("sourceType", "posts");
            assertThat(hit.metadata()).containsEntry("title", "西湖");
            assertThat(((Number) hit.metadata().get("sourceId")).longValue()).isEqualTo(901L);
        });
    }

    @Test
    void deleteBySource_removesOnlyThatSource() {
        store.add(List.of(chunk("posts:902:0", "故宫", "故宫一日游攻略。", "posts", 902, 0)));
        store.add(List.of(chunk("posts:903:0", "故宫", "故宫深度游攻略续篇。", "posts", 903, 0)));

        store.deleteBySource("posts", 902);

        List<KnowledgeVectorStore.Hit> hits = store.search("故宫攻略", 10);
        assertThat(hits).noneSatisfy(hit ->
                assertThat(((Number) hit.metadata().get("sourceId")).longValue()).isEqualTo(902L));
        assertThat(hits).anySatisfy(hit ->
                assertThat(((Number) hit.metadata().get("sourceId")).longValue()).isEqualTo(903L));
    }

    @Test
    void reset_emptiesTheCollection() {
        store.add(List.of(chunk("posts:904:0", "西湖", "重置前的内容。", "posts", 904, 0)));

        store.reset();

        assertThat(store.search("西湖", 10)).isEmpty();
    }

    @Test
    void search_onAFreshCollection_returnsEmpty() {
        KnowledgeVectorStore fresh = new ChromaKnowledgeVectorStore(
                ChromaApi.builder().baseUrl(CHROMA.getEndpoint()).build(),
                deterministicEmbeddingModel(),
                "wanderchina_knowledge_empty");

        assertThat(fresh.search("任意问题", 4)).isEmpty();
    }

    @Test
    void unreachableStore_mapsToKnowledgeBaseUnavailableException() {
        KnowledgeVectorStore unreachable = new ChromaKnowledgeVectorStore(
                ChromaApi.builder().baseUrl("http://127.0.0.1:1").build(),
                deterministicEmbeddingModel(),
                "wanderchina_knowledge_unreachable");

        assertThatThrownBy(() -> unreachable.search("西湖", 4))
                .isInstanceOf(KnowledgeBaseUnavailableException.class);
    }

    private static KnowledgeVectorStore.Chunk chunk(String id, String title, String body,
            String sourceType, long sourceId, int chunkIndex) {
        Map<String, Object> metadata = Map.of(
                "sourceType", sourceType,
                "sourceId", sourceId,
                "title", title,
                "chunkIndex", chunkIndex);
        return new KnowledgeVectorStore.Chunk(id, "《" + title + "》 (" + sourceType + ")\n" + body, metadata);
    }

    /**
     * Deterministic two-dimension fake: 西湖-flavored text maps to (1,0), 故宫-flavored text to
     * (0,1), everything else to (0.5,0.5) — enough for stable nearest-neighbour assertions
     * without any provider.
     */
    private static EmbeddingModel deterministicEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = new ArrayList<>();
                List<String> texts = request.getInstructions();
                for (int i = 0; i < texts.size(); i++) {
                    embeddings.add(new Embedding(vectorFor(texts.get(i)), i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return vectorFor(document.getText());
            }

            @Override
            public int dimensions() {
                return 2;
            }

            private float[] vectorFor(String text) {
                if (text.contains("西湖")) {
                    return new float[] { 1f, 0f };
                }
                if (text.contains("故宫")) {
                    return new float[] { 0f, 1f };
                }
                return new float[] { 0.5f, 0.5f };
            }
        };
    }
}
