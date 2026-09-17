package com.first.app.repository;

import com.first.app.entity.AttractionFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttractionFavoriteRepository extends JpaRepository<AttractionFavorite, Long> {

    Optional<AttractionFavorite> findByAttractionIdAndUserId(Long attractionId, Long userId);

    long countByAttractionId(Long attractionId);
}
