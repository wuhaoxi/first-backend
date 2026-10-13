package com.first.app.dto;

/**
 * Counts reported by the knowledge-base reindex endpoint: indexed documents per source type plus
 * the total number of chunks written to the vector store.
 */
public record ReindexResponse(int posts, int attractions, int cities, int chunks) {
}
