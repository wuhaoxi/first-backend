package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.integration.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeSearchServiceTest {

    private static final KnowledgeBaseProperties CONFIGURED =
            new KnowledgeBaseProperties("test-key", null, null, 0, 4, 0, 0, null, null);

    private static final KnowledgeBaseProperties KEYLESS =
            new KnowledgeBaseProperties(null, null, null, 0, 4, 0, 0, null, null);

    private static final String UNAVAILABLE_NOTICE =
            "Knowledge base is currently unavailable; site content cannot be searched right now.";

    private final KnowledgeVectorStore store = mock(KnowledgeVectorStore.class);

    private KnowledgeSearchService service(KnowledgeBaseProperties properties) {
        return new KnowledgeSearchService(store, properties);
    }

    @Test
    void search_formatsHitsAsLabeledPlainTextBlocks() {
        when(store.search("西湖", 4)).thenReturn(List.of(
                new KnowledgeVectorStore.Hit("《西湖》 (attractions)\nWest Lake 著名景点",
                        Map.of("title", "西湖", "sourceType", "attractions")),
                new KnowledgeVectorStore.Hit("《杭州》 (cities)\n人间天堂",
                        Map.of("title", "杭州", "sourceType", "cities"))));

        String result = service(CONFIGURED).search("西湖");

        assertThat(result).isEqualTo(
                "[1] 《西湖》 (attractions)\nWest Lake 著名景点\n\n"
                        + "[2] 《杭州》 (cities)\n人间天堂");
    }

    @Test
    void search_passesConfiguredTopK() {
        KnowledgeBaseProperties topKTwo =
                new KnowledgeBaseProperties("test-key", null, null, 0, 2, 0, 0, null, null);
        when(store.search("q", 2)).thenReturn(List.of());

        service(topKTwo).search("q");

        verify(store).search("q", 2);
    }

    @Test
    void search_emptyHits_returnsNoRelevantContentNotice() {
        when(store.search("q", 4)).thenReturn(List.of());

        String result = service(CONFIGURED).search("q");

        assertThat(result).isEqualTo("No relevant content found in the knowledge base for this question.");
    }

    @Test
    void search_storeUnavailable_returnsPlainNoticeAndNeverThrows() {
        when(store.search("q", 4)).thenThrow(new KnowledgeBaseUnavailableException("store down"));

        String result = service(CONFIGURED).search("q");

        assertThat(result).isEqualTo(UNAVAILABLE_NOTICE);
    }

    @Test
    void search_blankKey_returnsUnavailableNoticeWithoutStoreCall() {
        String result = service(KEYLESS).search("q");

        assertThat(result).isEqualTo(UNAVAILABLE_NOTICE);
        verifyNoInteractions(store);
    }
}
