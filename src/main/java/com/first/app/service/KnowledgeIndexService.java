package com.first.app.service;

import com.first.app.config.KnowledgeBaseProperties;
import com.first.app.dto.ReindexResponse;
import com.first.app.entity.Attraction;
import com.first.app.entity.AttractionStatus;
import com.first.app.entity.City;
import com.first.app.entity.CityStatus;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.exception.KnowledgeBaseUnavailableException;
import com.first.app.integration.KnowledgeVectorStore;
import com.first.app.repository.AttractionRepository;
import com.first.app.repository.CityRepository;
import com.first.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the vector index in sync with published content.
 *
 * <p>Post syncs are driven by post change events and run after commit, so they must never break
 * the posts API: store failures are swallowed with a WARN. Admin-triggered reindexing instead
 * propagates {@link KnowledgeBaseUnavailableException} so the HTTP layer can answer 503. Without
 * an embedding key every operation is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexService {

    private final KnowledgeVectorStore store;

    private final KnowledgeBaseProperties properties;

    private final KnowledgeChunker chunker;

    private final PostRepository postRepository;

    private final AttractionRepository attractionRepository;

    private final CityRepository cityRepository;

    /**
     * After-commit hook for post writes: re-reads the committed post (absent → remove) and syncs
     * it through {@link #syncPost(long)}, which swallows store failures so post writes never
     * fail because of the knowledge base.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostChanged(PostChangedEvent event) {
        syncPost(event.postId());
    }

    /**
     * Indexes a post when it is PUBLISHED, removes it from the index otherwise (draft or
     * deleted). Store failures are logged and swallowed — the posts API must not fail because
     * the knowledge base is down.
     */
    public void syncPost(long postId) {
        if (!properties.configured()) {
            return;
        }
        try {
            postRepository.findById(postId)
                    .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                    .ifPresentOrElse(
                            post -> store.add(chunker.chunkPost(post)),
                            () -> store.deleteBySource("posts", postId));
        } catch (KnowledgeBaseUnavailableException ex) {
            log.warn("Knowledge index sync skipped for post {}: {}", postId, ex.getMessage());
        }
    }

    /**
     * Rebuilds the whole index from PUBLISHED posts, attractions, and cities. Resets first so
     * unpublished or deleted content disappears. Store failures propagate to the caller.
     */
    public ReindexResponse reindexAll() {
        if (!properties.configured()) {
            return new ReindexResponse(0, 0, 0, 0);
        }
        store.reset();
        List<Post> posts = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
        List<Attraction> attractions = attractionRepository.findByStatus(AttractionStatus.PUBLISHED);
        List<City> cities = cityRepository.findByStatus(CityStatus.PUBLISHED);

        List<KnowledgeVectorStore.Chunk> chunks = new ArrayList<>();
        posts.forEach(post -> chunks.addAll(chunker.chunkPost(post)));
        attractions.forEach(attraction -> chunks.addAll(chunker.chunkAttraction(attraction)));
        cities.forEach(city -> chunks.addAll(chunker.chunkCity(city)));
        if (!chunks.isEmpty()) {
            store.add(chunks);
        }
        return new ReindexResponse(posts.size(), attractions.size(), cities.size(), chunks.size());
    }
}
