package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.entity.Attraction;
import com.first.app.entity.City;
import com.first.app.entity.Post;
import com.first.app.integration.KnowledgeVectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns published source entities into embeddable chunks: deterministic ids ({@code
 * {sourceType}:{sourceId}:{chunkIndex}}, so re-indexing overwrites instead of duplicating), a
 * {@code 《title》 (sourceType)} text prefix feeding both retrieval and the plain-text citation a
 * model can echo, and citation metadata. Splitting is char-based (800/100 by default) — well
 * inside the embedding model's input limits for Chinese content.
 */
@Component
public class KnowledgeChunker {

    private final int chunkSizeChars;

    private final int chunkOverlapChars;

    public KnowledgeChunker(KnowledgeBaseProperties properties) {
        this.chunkSizeChars = properties.chunkSizeChars();
        this.chunkOverlapChars = properties.chunkOverlapChars();
    }

    public List<KnowledgeVectorStore.Chunk> chunkPost(Post post) {
        return chunk("posts", post.getId(), post.getTitle(), post.getContent());
    }

    public List<KnowledgeVectorStore.Chunk> chunkAttraction(Attraction attraction) {
        String body = joinSegments(
                attraction.getName(),
                attraction.getCategory() != null ? attraction.getCategory().name() : null,
                attraction.getCity(),
                attraction.getTags() != null ? String.join(" ", attraction.getTags()) : null,
                attraction.getSummary(),
                attraction.getDescription(),
                attraction.getOpeningHours(),
                attraction.getTicketPrice());
        return chunk("attractions", attraction.getId(), attraction.getNameZh(), body);
    }

    public List<KnowledgeVectorStore.Chunk> chunkCity(City city) {
        String body = joinSegments(city.getName(), city.getBestSeason(), city.getDescription());
        return chunk("cities", city.getId(), city.getNameZh(), body);
    }

    List<KnowledgeVectorStore.Chunk> chunk(String sourceType, long sourceId, String title, String body) {
        String normalizedBody = body == null ? "" : body.trim();
        String prefix = "《" + title + "》 (" + sourceType + ")\n";
        List<KnowledgeVectorStore.Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int start = 0;
        while (true) {
            int end = Math.min(start + chunkSizeChars, normalizedBody.length());
            String slice = normalizedBody.substring(start, end);
            Map<String, Object> metadata = Map.of(
                    "sourceType", sourceType,
                    "sourceId", sourceId,
                    "title", title,
                    "chunkIndex", chunkIndex);
            chunks.add(new KnowledgeVectorStore.Chunk(
                    sourceType + ":" + sourceId + ":" + chunkIndex, prefix + slice, metadata));
            chunkIndex++;
            if (end == normalizedBody.length()) {
                break;
            }
            start += chunkSizeChars - chunkOverlapChars;
        }
        return chunks;
    }

    private static String joinSegments(String... segments) {
        return Stream.of(segments)
                .filter(segment -> segment != null && !segment.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
