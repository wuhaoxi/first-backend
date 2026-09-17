package com.first.app.service;

import com.first.app.entity.AttractionFavorite;
import com.first.app.repository.AttractionFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Favorite toggle for attractions. Mirrors {@code BookmarkService}: favoriting
 * inserts a row, un-favoriting hard-deletes it, and no stored counter is maintained.
 */
@Service
@RequiredArgsConstructor
public class AttractionFavoriteService {

    private final AttractionFavoriteRepository attractionFavoriteRepository;
    private final AttractionService attractionService;

    @Transactional
    public boolean toggle(Long attractionId, Long userId) {
        attractionService.findByIdPublic(attractionId);

        Optional<AttractionFavorite> existing =
                attractionFavoriteRepository.findByAttractionIdAndUserId(attractionId, userId);
        if (existing.isPresent()) {
            attractionFavoriteRepository.delete(existing.get());
            return false;
        }

        attractionFavoriteRepository.save(AttractionFavorite.builder()
                .attractionId(attractionId)
                .userId(userId)
                .build());
        return true;
    }
}
