package com.first.app.service;

import com.first.app.dto.AttractionResponse;
import com.first.app.repository.AttractionCommentRepository;
import com.first.app.repository.AttractionFavoriteRepository;
import org.springframework.stereotype.Service;

/**
 * Enriches a single {@link AttractionResponse} with live interaction data:
 * the non-deleted comment count, the favorite count (stored curated base +
 * live favorites), and — for authenticated requests — the requesting user's
 * favorite state. Anonymous requests leave {@code favorited} null.
 */
@Service
public class AttractionInteractionEnricher {

    private final AttractionCommentRepository attractionCommentRepository;
    private final AttractionFavoriteRepository attractionFavoriteRepository;

    public AttractionInteractionEnricher(AttractionCommentRepository attractionCommentRepository,
                                         AttractionFavoriteRepository attractionFavoriteRepository) {
        this.attractionCommentRepository = attractionCommentRepository;
        this.attractionFavoriteRepository = attractionFavoriteRepository;
    }

    public void enrich(AttractionResponse response, Long userId) {
        if (response == null || response.getId() == null) {
            return;
        }
        response.setCommentCount(
                (int) attractionCommentRepository.countByAttractionIdAndDeletedFalse(response.getId()));
        long liveFavorites = attractionFavoriteRepository.countByAttractionId(response.getId());
        response.setFavoriteCount(response.getFavoriteCount() + (int) liveFavorites);
        if (userId != null) {
            response.setFavorited(attractionFavoriteRepository
                    .findByAttractionIdAndUserId(response.getId(), userId)
                    .isPresent());
        }
    }
}
