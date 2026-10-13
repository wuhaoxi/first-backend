package com.first.app.integration;

import java.util.List;
import java.util.Map;

/**
 * Storage port for the assistant knowledge base. Implementations embed and persist
 * {@link Chunk}s and serve nearest-neighbour {@link Hit}s; failures surface as
 * {@code KnowledgeBaseUnavailableException}.
 */
public interface KnowledgeVectorStore {

    /** Embeds and upserts the given chunks (same chunk id overwrites — deterministic ids make this idempotent). */
    void add(List<Chunk> chunks);

    /** Removes every chunk belonging to one source document. */
    void deleteBySource(String sourceType, long sourceId);

    /** Empties the whole index (used before a full rebuild). */
    void reset();

    /** Returns up to {@code topK} nearest chunks for the query text. */
    List<Hit> search(String query, int topK);

    /** A chunk ready to be embedded: deterministic id, prefixed text, citation metadata. */
    record Chunk(String id, String text, Map<String, Object> metadata) {
    }

    /** A retrieved chunk: its stored text and metadata (sourceType/sourceId/title/chunkIndex). */
    record Hit(String text, Map<String, Object> metadata) {
    }
}
