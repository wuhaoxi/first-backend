package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.integration.KnowledgeVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats knowledge-base retrieval for the assistant tool. Results become labeled plain-text
 * blocks — {@code [n] 《title》 (sourceType)} followed by the chunk body — and every degradation
 * scenario (no hits, blank key, store down) yields a short plain-text notice instead of an
 * exception, so the SSE stream always stays alive. No URLs or links are ever produced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private static final String NO_RESULTS_TEXT =
            "No relevant content found in the knowledge base for this question.";

    private static final String UNAVAILABLE_TEXT =
            "Knowledge base is currently unavailable; site content cannot be searched right now.";

    private final KnowledgeVectorStore store;

    private final KnowledgeBaseProperties properties;

    /** Retrieves up to {@code top-k} chunks for the question; never throws. */
    public String search(String question) {
        if (!properties.configured()) {
            return UNAVAILABLE_TEXT;
        }
        try {
            List<KnowledgeVectorStore.Hit> hits = store.search(question, properties.topK());
            if (hits.isEmpty()) {
                return NO_RESULTS_TEXT;
            }
            List<String> blocks = new ArrayList<>(hits.size());
            for (int i = 0; i < hits.size(); i++) {
                blocks.add(formatHit(i + 1, hits.get(i)));
            }
            return String.join("\n\n", blocks);
        } catch (KnowledgeBaseUnavailableException ex) {
            log.warn("Knowledge search unavailable: {}", ex.getMessage());
            return UNAVAILABLE_TEXT;
        }
    }

    private static String formatHit(int index, KnowledgeVectorStore.Hit hit) {
        String title = String.valueOf(hit.metadata().getOrDefault("title", ""));
        String sourceType = String.valueOf(hit.metadata().getOrDefault("sourceType", ""));
        String label = "[" + index + "] 《" + title + "》 (" + sourceType + ")";
        // Chunks carry the label line as their own prefix; the numbered label replaces it.
        String prefix = "《" + title + "》 (" + sourceType + ")\n";
        String body = hit.text().startsWith(prefix) ? hit.text().substring(prefix.length()) : hit.text();
        return label + "\n" + body;
    }
}
